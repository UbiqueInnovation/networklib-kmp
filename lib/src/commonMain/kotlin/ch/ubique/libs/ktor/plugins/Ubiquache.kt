package ch.ubique.libs.ktor.plugins

import ch.ubique.libs.ktor.CacheControlValue
import ch.ubique.libs.ktor.XUbiquache
import ch.ubique.libs.ktor.cache.CacheManager
import ch.ubique.libs.ktor.cache.db.NetworkCacheDatabase
import io.ktor.client.HttpClient
import io.ktor.client.call.HttpClientCall
import io.ktor.client.plugins.HttpClientPlugin
import io.ktor.client.plugins.cache.InvalidCacheStateException
import io.ktor.client.plugins.cache.storage.createResponse
import io.ktor.client.request.*
import io.ktor.client.statement.HttpReceivePipeline
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.request
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import io.ktor.util.AttributeKey
import io.ktor.util.InternalAPI
import io.ktor.util.KtorDsl
import io.ktor.util.date.GMTDate
import io.ktor.util.pipeline.PipelineContext
import io.ktor.util.pipeline.PipelinePhase
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.Job

class Ubiquache private constructor(val name: String) {

	/**
	 * Plugin to support multi-date disk caching.
	 *
	 *     HttpClient(...) {
	 *         install(Ubiquache) {
	 *             name = "ubiquache" // optional, if you want multiple independent caches
	 *         }
	 *     }
	 */
	companion object Plugin : HttpClientPlugin<Config, Ubiquache> {

		override val key: AttributeKey<Ubiquache> = AttributeKey("Ubiquache")

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

				val cacheHandle = cacheManager.obtainCacheHandle(context)
				val cacheControl = parseHeaderValue(context.headers[HttpHeaders.CacheControl])

				if (CacheControlValue.ONLY_IF_CACHED in cacheControl) {
					// force cached response, do not make network request
					if (cacheHandle.hasValidCachedResource()) {
						val cachedResponse = cacheManager.getCachedResponse(cacheHandle, scope, context)
						proceedWithCache(scope, cachedResponse.call)
					} else {
						proceedWithCachedResourceNotFound(scope)
					}
					return@intercept
				} else {
					cacheHandle.updateLastAccessed()
				}

				if (CacheControlValue.NO_CACHE in cacheControl) {
					// request as-is
					proceedWith(context)
				} else if (cacheHandle.shouldBeRefreshedButIsNotExpired()) {
					// valid resource in cache, but should be refreshed, fallback to cached resource on network error
					context.attributes.put(attributeKeyUseCacheOnFailure, true)
					context.withCacheHeader(cacheHandle)
					proceedWith(context)
				} else if (cacheHandle.hasValidCachedResource()) {
					// cache hit
					val cachedResponse = cacheManager.getCachedResponse(cacheHandle, scope, context)
					proceedWithCache(scope, cachedResponse.call)
				} else {
					context.withCacheHeader(cacheHandle)
					proceedWith(context)
				}
			}

			scope.receivePipeline.intercept(HttpReceivePipeline.State) { response ->
				if (response.call.request.method != HttpMethod.Get || !response.call.request.url.protocol.canStore()) return@intercept

				val useCacheOnFailure = response.call.request.attributes.getOrNull(attributeKeyUseCacheOnFailure) == true

				if (useCacheOnFailure && response.status.value in 500..599) {
					response.complete()
					val responseFromCache = plugin.findAndRefresh(response.call.request, response)
						?: throw InvalidCacheStateException(response.call.request.url)
					proceedWith(responseFromCache)
					return@intercept
				}

				if (response.status == HttpStatusCode.NotModified) {
					response.complete()
					val responseFromCache = plugin.findAndRefresh(response.call.request, response)
						?: throw InvalidCacheStateException(response.call.request.url)
					proceedWith(responseFromCache)
					return@intercept
				}

				if (response.isCacheable()) {
					val cachedData = plugin.cacheResponse(response)
					if (cachedData != null) {
						val reusableResponse = cachedData.createResponse(scope, response.request, response.coroutineContext)
						proceedWith(reusableResponse)
						return@intercept
					}
				}
			}
		}

		private suspend fun PipelineContext<Any, HttpRequestBuilder>.proceedWithCache(
			scope: HttpClient,
			cachedCall: HttpClientCall
		) {
			finish()
			proceedWith(cachedCall)
		}

		@OptIn(InternalAPI::class)
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
			val call = HttpClientCall(scope, request, response)
			proceedWith(call)
		}

		private fun URLProtocol.canStore(): Boolean = name == "http" || name == "https"

		private fun HttpResponse.isCacheable(): Boolean {
			if (!status.isSuccess()) return false

			val requestCacheControl = parseHeaderValue(call.request.headers[HttpHeaders.CacheControl])
			if (CacheControlValue.NO_STORE in requestCacheControl) return false

			val responseCacheControl = parseHeaderValue(headers[HttpHeaders.CacheControl])
			if (CacheControlValue.NO_CACHE in responseCacheControl) return false
			if (CacheControlValue.NO_STORE in responseCacheControl) return false
		}

		private fun HttpRequestBuilder.withCacheHeader(cacheHandle: Any) {
			cacheHandle.headers[HttpHeaders.ETag]?.let { etag ->
				header(HttpHeaders.IfNoneMatch, etag)
			}
			cacheHandle.headers[HttpHeaders.LastModified]?.let { lastModified ->
				header(HttpHeaders.IfModifiedSince, lastModified)
			}
		}

		private fun HttpResponse.complete() {
			val job = coroutineContext[Job]!! as CompletableJob
			job.complete()
		}
	}

	@KtorDsl
	class Config {
		var name: String = "ubiquache"
	}

}
