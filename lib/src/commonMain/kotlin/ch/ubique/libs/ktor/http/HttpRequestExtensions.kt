package ch.ubique.libs.ktor.http

import io.ktor.client.call.HttpClientCall
import io.ktor.client.request.HttpRequest
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.takeFrom
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.Url
import io.ktor.http.content.OutgoingContent
import io.ktor.util.Attributes

internal fun HttpRequest.toHttpRequestData(): HttpRequestData {
	return HttpRequestBuilder().takeFrom(this).build()
}

internal fun HttpRequestData.toHttpRequest(): HttpRequest {
	val data = this
	return object : HttpRequest {
		override val call: HttpClientCall
			get() = throw IllegalStateException("This request has no call")
		override val method: HttpMethod = data.method
		override val url: Url = data.url
		override val attributes: Attributes = data.attributes
		override val content: OutgoingContent = data.body
		override val headers: Headers = data.headers
	}
}

internal fun HttpRequest.expectNotModified(): Boolean {
	return headers.contains(HttpHeaders.IfNoneMatch) || headers.contains(HttpHeaders.IfModifiedSince)
}
