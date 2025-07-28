package ch.ubique.libs.ktor.plugins

import ch.ubique.libs.ktor.cache.CacheHandle
import ch.ubique.libs.ktor.cache.CacheManager
import ch.ubique.libs.ktor.cache.db.NetworkCacheDatabase
import ch.ubique.libs.ktor.cache.db.RecoveringDriver
import ch.ubique.libs.ktor.common.deleteRecursively
import ch.ubique.libs.ktor.http.*
import io.ktor.client.HttpClient
import io.ktor.client.call.HttpClientCall
import io.ktor.client.plugins.HttpClientPlugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.HttpSendPipeline
import io.ktor.client.request.header
import io.ktor.client.statement.HttpReceivePipeline
import io.ktor.client.statement.request
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import io.ktor.util.AttributeKey
import io.ktor.util.date.GMTDate
import io.ktor.util.pipeline.PipelineContext
import io.ktor.util.pipeline.PipelinePhase
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.KtorDsl
import kotlinx.io.files.Path

class Ubiquache private constructor(
	val name: String,
	val maxSize: Long,
) {

	/**
	 * Plugin to support disk caching with multiple levels of expiration.
	 *
	 *     HttpClient(...) {
	 *         install(Ubiquache) {
	 *             name = "ubiquache" // optional, if you want multiple independent caches
	 *         }
	 *     }
	 */
	companion object Plugin : HttpClientPlugin<Config, Ubiquache> {

		const val CACHE_SIZE_AUTO = CacheManager.CACHE_SIZE_AUTO

		override val key: AttributeKey<Ubiquache> = AttributeKey("Ubiquache")

		private val attributeKeyCacheHandle = AttributeKey<CacheHandle>("CacheHandle")
		private val attributeKeyUseCacheOnFailure = AttributeKey<Boolean>("UseCacheOnFailure")

		override fun prepare(block: Config.() -> Unit): Ubiquache {
			val config = Config().apply(block)
			val name = config.name
			require(name.matches(Regex("[A-Za-z0-9._\\-]+"))) { "Cache name must only use A-Za-z0-9._-" }
			return Ubiquache(name, config.maxSize)
		}

		override fun install(plugin: Ubiquache, scope: HttpClient) {
			val cacheManager = plugin.getCacheManager()

			val CachePhase = PipelinePhase("Ubiquache")
			scope.sendPipeline.insertPhaseAfter(HttpSendPipeline.State, CachePhase)

			scope.sendPipeline.intercept(CachePhase) { content ->
				val cacheControl = parseHeaderValue(context.headers[HttpHeaders.CacheControl])

				if (content !is OutgoingContent.NoContent || context.method != HttpMethod.Get || !context.url.protocol.canStore()) {
					if (CacheControlValue.ONLY_IF_CACHED in cacheControl) {
						proceedWithCachedResourceNotFound(scope)
					}
					return@intercept
				}

				val requestData = context.build()
				val cacheHandle = cacheManager.obtainCacheHandle(requestData)
				context.attributes.put(attributeKeyCacheHandle, cacheHandle)

				cacheManager.withLock(cacheHandle) {
					if (CacheControlValue.ONLY_IF_CACHED in cacheControl) {
						// force cached response, do not make network request
						if (cacheHandle.hasValidCachedResource()) {
							val ubiquacheHeader = headersOf(HttpHeaders.XUbiquache, CacheControlValue.ONLY_IF_CACHED.value)
							val cachedResponse = cacheManager.getCachedResponse(cacheHandle, scope, requestData, ubiquacheHeader)
							proceedWithCache(cachedResponse.call)
						} else {
							proceedWithCachedResourceNotFound(scope)
						}
						return@intercept
					} else {
						cacheHandle.updateLastAccessed()
					}

					if (CacheControlValue.NO_CACHE in cacheControl) {
						// request as-is
					} else if (cacheHandle.shouldBeRefreshedButIsNotExpired()) {
						// valid resource in cache, but should be refreshed, fallback to cached resource on network error
						context.attributes.put(attributeKeyUseCacheOnFailure, true)
						context.withCacheHeader(cacheHandle)
					} else if (cacheHandle.hasValidCachedResource()) {
						// cache hit
						val cachedResponse = cacheManager.getCachedResponse(cacheHandle, scope, requestData)
						proceedWithCache(cachedResponse.call)
						return@intercept
					} else {
						// request using etag/last-modified
						context.withCacheHeader(cacheHandle)
					}

					try {
						proceed()
					} catch (e: Throwable) {
						if (context.attributes.getOrNull(attributeKeyUseCacheOnFailure) == true && cacheHandle.hasValidCachedResource()) {
							val cachedResponse = cacheManager.getCachedResponse(cacheHandle, scope, requestData)
							proceedWithCache(cachedResponse.call)
						} else {
							throw e
						}
					}
				}
			}

			scope.receivePipeline.intercept(HttpReceivePipeline.State) { response ->
				val requestAttributes = response.request.attributes
				val cacheHandle = requestAttributes.getOrNull(attributeKeyCacheHandle) ?: return@intercept
				val useCacheOnFailure = requestAttributes.getOrNull(attributeKeyUseCacheOnFailure) == true

				if (useCacheOnFailure && response.status.value in 500..599) {
					val cachedResponse = cacheManager.getCachedResponse(cacheHandle, scope, response.request.toHttpRequestData())
					response.complete()
					proceedWith(cachedResponse)
					return@intercept
				}

				if (response.status == HttpStatusCode.NotModified && response.request.expectNotModified()) {
					cacheManager.updateCacheHeaders(cacheHandle, response)
					val cachedResponse = cacheManager.getCachedResponse(cacheHandle, scope, response.request.toHttpRequestData())
					response.complete()
					proceedWith(cachedResponse)
					return@intercept
				}

				if (response.isCacheable()) {
					val cachedResponse = cacheManager.storeCacheableResponse(cacheHandle, response)
					if (cachedResponse != null) {
						proceedWith(cachedResponse)
						return@intercept
					}
				} else {
					cacheHandle.removeFromCache()
				}
			}

			scope.receivePipeline.intercept(HttpReceivePipeline.After) { _ ->
				cacheManager.asyncLazyCleanup()
			}
		}

		private suspend fun PipelineContext<Any, HttpRequestBuilder>.proceedWithCache(cachedCall: HttpClientCall) {
			finish()
			proceedWith(cachedCall)
		}

		private suspend fun PipelineContext<Any, HttpRequestBuilder>.proceedWithCachedResourceNotFound(scope: HttpClient) {
			finish()
			val request = context.build()
			val response = HttpResponseData(
				statusCode = HttpStatusCode.GatewayTimeout, //developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Cache-Control#other
				requestTime = GMTDate(),
				headers = headersOf(HttpHeaders.XUbiquache, CacheControlValue.ONLY_IF_CACHED.value),
				version = HttpProtocolVersion.HTTP_1_1,
				body = ByteReadChannel(ByteArray(0)),
				callContext = request.executionContext
			)
			@OptIn(InternalAPI::class)
			val call = HttpClientCall(scope, request, response)
			proceedWith(call)
		}

		private fun URLProtocol.canStore(): Boolean = name == "http" || name == "https"

		private fun HttpRequestBuilder.withCacheHeader(cacheHandle: CacheHandle) {
			val etag = cacheHandle.getETag()
			if (etag != null) {
				header(HttpHeaders.IfNoneMatch, etag)
				return
			}
			val lastModified = cacheHandle.getLastModified()
			if (lastModified > 0) {
				header(HttpHeaders.IfModifiedSince, GMTDate(lastModified).toHttpDateString())
				return
			}
		}
	}

	@KtorDsl
	class Config {
		/**
		 * Name of the cache. If you want to have multiple independent caches, you can specify a different name.
		 */
		var name: String = "ubiquache"

		/**
		 * Maximum size of the cache in bytes.
		 * Default is [CACHE_SIZE_AUTO] which will automatically determine the size based on available disk space.
		 */
		var maxSize: Long = CACHE_SIZE_AUTO
	}

	private var cacheManager: CacheManager? = null

	private fun getCacheManager(): CacheManager {
		return cacheManager ?: run {
			println("getCacheManager creating new cacheManager")
			val cacheDir = UbiquacheConfig.getCacheDir(name)
			println("getCacheManager after getCacheDir")
			val recoveringDriver = RecoveringDriver(
				driverProvider = { UbiquacheConfig.createDriver(cacheDir) },
				onFatalError = { error ->
					println("getCacheManager recoveringDriver onFatalError $error")
					cacheDir?.deleteRecursively()
					error.printStackTrace()
				}
			)
			println("getCacheManager after recoveringDriver")
			val db = NetworkCacheDatabase(recoveringDriver)
			println("getCacheManager before return")
			return CacheManager(cacheDir ?: Path(""), db, maxSize).also { cacheManager = it }
		}
	}

	/**
	 * Clear the cache, deleting all files in the cache. Pending cache operations might fail.
	 */
	suspend fun clearCache() {
		getCacheManager().clearCache()
	}

	/**
	 * Clear the cache for a specific URL or all URLs with a common prefix.
	 */
	suspend fun clearCache(url: String, isPrefix: Boolean = false) {
		getCacheManager().clearCache(url, isPrefix)
	}

	/**
	 * Get the current cache size in bytes.
	 */
	fun usedCacheSize(): Long {
		return getCacheManager().usedCacheSize()
	}

	/**
	 * Get the maximum cache size in bytes.
	 */
	fun maxCacheSize(): Long {
		return getCacheManager().maxCacheSize()
	}

}
