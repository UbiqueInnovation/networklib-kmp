@file:Suppress("NOTHING_TO_INLINE")

package ch.ubique.libs.ktor

import ch.ubique.libs.ktor.cache.CacheManager
import ch.ubique.libs.ktor.common.deleteRecursively
import ch.ubique.libs.ktor.common.runBlockingOrThrowIfNotSupported
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
import kotlinx.coroutines.*
import kotlinx.io.files.Path
import kotlin.random.Random
import kotlin.random.nextUInt
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
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
@OptIn(ExperimentalStdlibApi::class, ExperimentalTime::class)
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
): HttpResponse = runBlockingOrThrowIfNotSupported {
	get(urlString, block)
}

/**
 * Executes an [HttpClient]'s GET request intended for the [MockEngine] returning the response as a String.
 */
fun HttpClient.getMockStringBlocking(
	urlString: String = "http://test/",
	block: HttpRequestBuilder.() -> Unit = {},
): String = runBlockingOrThrowIfNotSupported {
	get(urlString, block).bodyAsText()
}

/**
 * Executes an [HttpClient]'s POST request intended for the [MockEngine] returning the response.
 */
fun HttpClient.postMockResponseBlocking(
	urlString: String = "http://test/",
	block: HttpRequestBuilder.() -> Unit = {},
): HttpResponse = runBlockingOrThrowIfNotSupported {
	post(urlString, block)
}

/**
 * Get the response body as a String.
 */
fun HttpResponse.bodyAsTextBlocking(): String = runBlockingOrThrowIfNotSupported {
	bodyAsText()
}

/**
 * Run the given request block and wait for the cache cleanup to complete.
 * @return the duration in milliseconds it took for the cache cleanup to complete.
 */
internal suspend inline fun runAndWaitForCacheCleanup(block: () -> Unit): Long {
	val deferred = CompletableDeferred<Unit>()
	val onCacheCleanupCompleted: (CacheManager) -> Unit = { deferred.complete(Unit) }
	CacheManager.cacheCleanupListeners.add(onCacheCleanupCompleted)
	block()
	val wait = measureTime {
		withContext(Dispatchers.Default.limitedParallelism(1)) {
			try {
				withTimeout(5.seconds) {
					deferred.await()
				}
			} catch (e: TimeoutCancellationException) {
				throw AssertionError("Expected cache cleanup, but did not complete in time", e)
			}
		}
	}
	return wait.inWholeMilliseconds
}

/**
 * Skip time until the next cache cleanup is due.
 */
fun skipTimeToNextCacheCleanup() {
	skipTime(CacheManager.CACHE_CLEANUP_INTERVAL + 1234)
}

/**
 * Delete the given path recursively, retrying up to 10 times.
 *
 * For unknown reasons, the deletion of files sometimes fails but succeeds on the next attempt.
 */
suspend fun Path.reallyDelete() {
	for (i in 1..10) {
		val deleted = deleteRecursively()
		if (deleted) break
		println("Deletion attempt #$i failed for $this")
		delay(1)
	}
}
