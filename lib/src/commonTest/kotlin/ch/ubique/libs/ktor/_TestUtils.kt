package ch.ubique.libs.ktor

import ch.ubique.libs.ktor.common.deleteRecursively
import ch.ubique.libs.ktor.plugins.Ubiquache
import ch.ubique.libs.ktor.plugins.UbiquacheConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HeadersBuilder
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlin.random.Random
import kotlin.random.nextUInt

/**
 * Create a [MockEngine] with a given block of code to handle requests.
 */
fun withServer(block: suspend MockRequestHandleScope.(number: Int, request: HttpRequestData) -> HttpResponseData): MockEngine {
	var number = 0
	val engine = MockEngine { request ->
		number++
		block(number, request)
	}
	return engine
}

inline fun HeadersBuilder.header(name: String, value: String) {
	append(name, value)
}

/**
 * Create a [MockEngine] with a given block of code to handle requests and execute the given block of code.
 */
@OptIn(ExperimentalStdlibApi::class)
internal inline fun MockEngine.testUbiquache(block: MockEngine.(client: HttpClient) -> Unit) {
	val testId = Clock.System.now().toEpochMilliseconds().toUInt().toHexString() + "-" + Random.nextUInt().toHexString()
	val cacheName = "ubiquache-test-$testId"
	try {
		val client = HttpClient(this) {
			install(Ubiquache) {
				name = cacheName
			}
		}
		block(this, client)
	} finally {
		UbiquacheConfig.getCacheDir(cacheName).deleteRecursively()
	}
}

/**
 * Executes an [HttpClient]'s GET request intended for the [MockEngine] returning the response.
 */
fun HttpClient.getMockResponseBlocking(
	urlString: String = "http://test/",
	block: HttpRequestBuilder.() -> Unit = {},
): HttpResponse = runBlocking {
	get(urlString, block)
}

/**
 * Executes an [HttpClient]'s GET request intended for the [MockEngine] returning the response as a String.
 */
fun HttpClient.getMockStringBlocking(
	urlString: String = "http://test/",
	block: HttpRequestBuilder.() -> Unit = {},
): String = runBlocking {
	get(urlString, block).bodyAsText()
}

/**
 * Executes an [HttpClient]'s POST request intended for the [MockEngine] returning the response.
 */
fun HttpClient.postMockResponseBlocking(
	urlString: String = "http://test/",
	block: HttpRequestBuilder.() -> Unit = {},
): HttpResponse = runBlocking {
	post(urlString, block)
}

fun HttpResponse.bodyAsTextBlocking(): String = runBlocking {
	bodyAsText()
}
