package ch.ubique.libs.ktor

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.runBlocking

/**
 * Executes an [HttpClient]'s GET request intended for the [MockEngine] returning the response.
 */
fun HttpClient.getMockResponseBlocking(
	urlString: String = "http://localhost/",
	block: HttpRequestBuilder.() -> Unit = {},
): HttpResponse = runBlocking {
	get(urlString, block)
}

/**
 * Executes an [HttpClient]'s GET request intended for the [MockEngine] returning the response as a String.
 */
fun HttpClient.getMockStringBlocking(
	urlString: String = "http://localhost/",
	block: HttpRequestBuilder.() -> Unit = {},
): String = runBlocking {
	get(urlString, block).bodyAsText()
}
