package ch.ubique.libs.ktor.plugins

import ch.ubique.libs.ktor.cache.CacheHandle
import ch.ubique.libs.ktor.cache.CacheManager
import ch.ubique.libs.ktor.cache.db.NetworkCacheDatabase
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

class Ubiquache private constructor(val name: String) {

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

		override val key: AttributeKey<Ubiquache> = AttributeKey("Ubiquache")

		private val attributeKeyCacheHandle = AttributeKey<CacheHandle>("CacheHandle")
		private val attributeKeyUseCacheOnFailure = AttributeKey<Boolean>("UseCacheOnFailure")

		override fun prepare(block: Config.() -> Unit): Ubiquache {
			val config = Config().apply(block)
			val name = config.name
			require(name.matches(Regex("[A-Za-z0-9._\\-]+"))) { "Cache name must only use A-Za-z0-9._-" }
			return Ubiquache(name)
		}

		override fun install(plugin: Ubiquache, scope: HttpClient) {
			val cacheDir = UbiquacheConfig.getCacheDir(plugin.name)
			val db = NetworkCacheDatabase(UbiquacheConfig.createDriver(cacheDir))
			val cacheManager = CacheManager(cacheDir, db)

			// see also
			io.ktor.client.plugins.cache.HttpCache

			val CachePhase = PipelinePhase("Ubiquache")
			scope.sendPipeline.insertPhaseAfter(HttpSendPipeline.State, CachePhase)

			scope.sendPipeline.intercept(CachePhase) { content ->
				if (content !is OutgoingContent.NoContent) return@intercept
				if (context.method != HttpMethod.Get || !context.url.protocol.canStore()) return@intercept

				val requestData = context.build()
				val cacheHandle = cacheManager.obtainCacheHandle(requestData)
				context.attributes.put(attributeKeyCacheHandle, cacheHandle)

				val cacheControl = parseHeaderValue(context.headers[HttpHeaders.CacheControl])
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
		var name: String = "ubiquache"
	}

}
