@file:Suppress("NOTHING_TO_INLINE")

package ch.ubique.libs.ktor

import ch.ubique.libs.ktor.cache.CacheManager
import ch.ubique.libs.ktor.common.deleteRecursively
import ch.ubique.libs.ktor.common.skipTime
import ch.ubique.libs.ktor.plugins.Ubiquache
import ch.ubique.libs.ktor.plugins.UbiquacheConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HeadersBuilder
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.random.Random
import kotlin.random.nextUInt
import kotlin.time.measureTime

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
internal inline fun MockEngine.testUbiquache(maxCacheSize: Long? = null, block: MockEngine.(client: HttpClient) -> Unit) {
	val testId = Clock.System.now().toEpochMilliseconds().toUInt().toHexString() + "-" + Random.nextUInt().toHexString()
	val cacheName = "ubiquache-test-$testId"
	try {
		val client = HttpClient(this) {
			install(Ubiquache) {
				name = cacheName
				if (maxCacheSize != null) {
					maxSize = maxCacheSize
				}
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

fun waitForCacheCleanupToBeCompleted() = measureTime {
	// FIXME: instead of randomly waiting, create some hook to wait for the actual cache cleanup job to be completed
	runBlocking { delay(3000) }
}.inWholeMilliseconds

fun skipTimeToNextCacheCleanup() {
	skipTime(CacheManager.CACHE_CLEANUP_INTERVAL + 1234)
}
