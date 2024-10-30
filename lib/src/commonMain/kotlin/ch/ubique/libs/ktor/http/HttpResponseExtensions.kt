package ch.ubique.libs.ktor.http

import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.RedirectResponseException
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText

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
