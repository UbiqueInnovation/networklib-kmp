package ch.ubique.libs.ktor.plugins

import ch.ubique.libs.ktor.CacheControl
import ch.ubique.libs.ktor.XUbiquache
import ch.ubique.libs.ktor.cache.db.CacheDatabaseDriverFactory
import ch.ubique.libs.ktor.cache.db.NetworkCacheDatabase
import io.ktor.client.HttpClient
import io.ktor.client.call.HttpClientCall
import io.ktor.client.plugins.HttpClientPlugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.HttpSendPipeline
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import io.ktor.util.AttributeKey
import io.ktor.util.InternalAPI
import io.ktor.util.KtorDsl
import io.ktor.util.date.GMTDate
import io.ktor.util.pipeline.PipelineContext
import io.ktor.util.pipeline.PipelinePhase
import io.ktor.utils.io.ByteReadChannel

class Ubiquache private constructor(val name: String) {

	/**
	 * Plugin to support multi-date disk caching.
	 *
	 * TODO: useful docs
	 *
	 *     HttpClient(...) {
	 *         install(Ubiquache) {
	 *             name = "ubiquache" // optional, if you want multiple independent caches
	 *         }
	 *     }
	 */
	companion object Plugin : HttpClientPlugin<Config, Ubiquache> {

		override val key: AttributeKey<Ubiquache> = AttributeKey("Ubiquache")

		override fun prepare(block: Config.() -> Unit): Ubiquache {
			val config = Config().apply(block)
			val name = config.name ?: "ubiquache"
			require(name.matches(Regex("[A-Za-z0-9._\\-]+"))) { "Cache name must only use A-Za-z0-9._-" }
			return Ubiquache(name)
		}

		override fun install(plugin: Ubiquache, scope: HttpClient) {
			val db = NetworkCacheDatabase(CacheDatabaseDriverFactory().createDriver(plugin.name))

			val CachePhase = PipelinePhase("Ubiquache")
			scope.sendPipeline.insertPhaseAfter(HttpSendPipeline.State, CachePhase)

			scope.sendPipeline.intercept(CachePhase) { content ->
				if (content !is OutgoingContent.NoContent) return@intercept
				if (context.method != HttpMethod.Get || !context.url.protocol.canStore()) return@intercept

				// handle only-if-cached
				val header = parseHeaderValue(context.headers[HttpHeaders.CacheControl])
				if (HeaderValue(CacheControl.ONLY_IF_CACHED) in header) {
					proceedWithCachedResourceNotFound(scope)
					return@intercept
				}

				// TODO: implement me ...
				// see also io.ktor.client.plugins.cache.HttpCache
			}
		}

		@OptIn(InternalAPI::class)
		private suspend fun PipelineContext<Any, HttpRequestBuilder>.proceedWithCachedResourceNotFound(scope: HttpClient) {
			finish()
			val request = context.build()
			val response = HttpResponseData(
				statusCode = HttpStatusCode.GatewayTimeout, //developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Cache-Control#other
				requestTime = GMTDate(),
				headers = headersOf(HttpHeaders.XUbiquache, CacheControl.ONLY_IF_CACHED),
				version = HttpProtocolVersion.HTTP_1_1,
				body = ByteReadChannel(ByteArray(0)),
				callContext = request.executionContext
			)
			val call = HttpClientCall(scope, request, response)
			proceedWith(call)
		}

		private fun URLProtocol.canStore(): Boolean = name == "http" || name == "https"

	}

	@KtorDsl
	class Config {
		var name: String? = null
	}

}
