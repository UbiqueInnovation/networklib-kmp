package ch.ubique.libs.ktor.http

import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.RedirectResponseException
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.http.parseHeaderValue
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.Job

/**
 * Throw a ResponseException or any of its subclasses if the status code is no good.
 */
suspend fun HttpResponse.throwIfNotSuccessful() {
	// as of HttpClientConfig<*>.addDefaultResponseValidation()
	when (status.value) {
		in 200..299 -> return
		in 300..399 -> throw RedirectResponseException(this, bodyAsTextOrElse())
		in 400..499 -> throw ClientRequestException(this, bodyAsTextOrElse())
		in 500..599 -> throw ServerResponseException(this, bodyAsTextOrElse())
		else -> throw ResponseException(this, bodyAsTextOrElse())
	}
}

private suspend fun HttpResponse.bodyAsTextOrElse(): String {
	return runCatching { bodyAsText() }.getOrElse { "<body failed decoding>" }
}

internal fun HttpResponse.complete() {
	val job = coroutineContext[Job]!! as CompletableJob
	job.complete()
}

internal fun HttpResponse.isCacheable(): Boolean {
	if (!status.isSuccess()) return false

	val requestCacheControl = parseHeaderValue(call.request.headers[HttpHeaders.CacheControl])
	if (CacheControlValue.NO_STORE in requestCacheControl) return false

	val responseCacheControl = parseHeaderValue(headers[HttpHeaders.CacheControl])
	if (CacheControlValue.NO_CACHE in responseCacheControl) return false
	if (CacheControlValue.NO_STORE in responseCacheControl) return false

	return true
}
