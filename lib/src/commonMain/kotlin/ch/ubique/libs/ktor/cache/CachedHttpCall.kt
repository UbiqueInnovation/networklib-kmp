package ch.ubique.libs.ktor.cache

import io.ktor.client.HttpClient
import io.ktor.client.call.HttpClientCall
import io.ktor.client.request.HttpRequest
import io.ktor.client.statement.HttpResponse
import io.ktor.http.Headers
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.InternalAPI
import kotlinx.coroutines.Job
import kotlinx.io.Source
import kotlin.coroutines.CoroutineContext

internal class CachedHttpCall(
	client: HttpClient,
	request: HttpRequest,
	response: HttpResponse,
	responseBody: Source
) : HttpClientCall(client) {
	init {
		this.request = CachedHttpRequest(this, request)
		this.response = CachedHttpResponse(this, responseBody, response)
	}
}

private class CachedHttpRequest(
	override val call: HttpClientCall,
	origin: HttpRequest
) : HttpRequest by origin

private class CachedHttpResponse(
	override val call: HttpClientCall,
	body: Source,
	origin: HttpResponse,
) : HttpResponse() {
	private val context = Job()
	override val status: HttpStatusCode = origin.status
	override val version: HttpProtocolVersion = origin.version
	override val requestTime: GMTDate = origin.requestTime
	override val responseTime: GMTDate = origin.responseTime
	override val headers: Headers = origin.headers
	override val coroutineContext: CoroutineContext = origin.coroutineContext + context

	@InternalAPI
	override val rawContent: ByteReadChannel = ByteReadChannel(body)
}
