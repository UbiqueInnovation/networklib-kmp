package ch.ubique.libs.ktor.plugins.ubiquache

import ch.ubique.libs.ktor.*
import ch.ubique.libs.ktor.cache.CacheManager
import ch.ubique.libs.ktor.common.coerceAtLeast
import ch.ubique.libs.ktor.common.now
import ch.ubique.libs.ktor.common.skipTime
import ch.ubique.libs.ktor.http.toHttpDateString
import ch.ubique.libs.ktor.plugins.Ubiquache
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.plugin
import io.ktor.http.HttpHeaders
import io.ktor.http.headers
import io.ktor.util.date.GMTDate
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours

class CacheCleanupTest {

	@Test
	fun clearCache() = runTest {
		val requestCount = mutableMapOf<String, Int>()
		withServer { number, request ->
			val count = requestCount.getOrElse(request.url.toString()) { 0 } + 1
			requestCount[request.url.toString()] = count
			respond(
				content = "#$count",
				headers = headers {
					header(HttpHeaders.Expires, GMTDate(now() + 9000L).toHttpDateString())
				}
			)
		}.testUbiquache { client ->
			val url1 = "http://test/%31/my_url"
			val url2 = "http://test/2/my_url"
			run {
				val response1 = client.getMockStringBlocking(url1)
				assertEquals("#1", response1)
				val response2 = client.getMockStringBlocking(url2)
				assertEquals("#1", response2)
			}
			client.plugin(Ubiquache).clearCache()
			run {
				val response1 = client.getMockStringBlocking(url1)
				assertEquals("#2", response1)
				val response2 = client.getMockStringBlocking(url2)
				assertEquals("#2", response2)
			}
			assertEquals(4, responseHistory.size)
		}
	}

	@Test
	fun clearByUrl() = runTest {
		withServer { number, request ->
			respond(
				content = "#$number",
				headers = headers {
					header(HttpHeaders.Expires, GMTDate(now() + 9000L).toHttpDateString())
				}
			)
		}.testUbiquache { client ->
			val url = "http://test/my.url"
			run {
				val response = client.getMockStringBlocking(url)
				assertEquals("#1", response)
			}
			client.plugin(Ubiquache).clearCache(url)
			run {
				val response = client.getMockStringBlocking(url)
				assertEquals("#2", response)
			}
			assertEquals(2, responseHistory.size)
		}
	}

	@Test
	fun clearByUrlPrefix() = runTest {
		val requestCount = mutableMapOf<String, Int>()
		withServer { number, request ->
			val count = requestCount.getOrElse(request.url.toString()) { 0 } + 1
			requestCount[request.url.toString()] = count
			respond(
				content = "#$count",
				headers = headers {
					header(HttpHeaders.Expires, GMTDate(now() + 9000L).toHttpDateString())
				}
			)
		}.testUbiquache { client ->
			val domain1 = "http://test/%31/"
			val url1 = "${domain1}my_url"
			val domain2 = "http://test/2/"
			val url2 = "${domain2}my_url"
			run {
				val response1 = client.getMockStringBlocking(url1)
				assertEquals("#1", response1)
				val response2 = client.getMockStringBlocking(url2)
				assertEquals("#1", response2)
			}
			client.plugin(Ubiquache).clearCache(domain1, isPrefix = true)
			run {
				val response1 = client.getMockStringBlocking(url1)
				assertEquals("#2", response1)
				val response2 = client.getMockStringBlocking(url2)
				assertEquals("#1", response2)
			}
			assertEquals(3, responseHistory.size)
		}
	}

	@Test
	fun autoCleanup_accessImmunity() = runTest {
		withServer { number, request ->
			respond(
				content = "#$number",
				headers = headers {
					header(HttpHeaders.Expires, GMTDate(now() + 1.hours.inWholeMilliseconds).toHttpDateString())
				}
			)
		}.testUbiquache(maxCacheSize = 1) { client ->
			val url1 = "http://test/1"
			val url2 = "http://test/2"

			runAndWaitForCacheCleanup {
				val response = client.getMockStringBlocking(url1)
				assertEquals("#1", response)
			}.also { waitDuration ->
				skipTime(CacheManager.CACHE_CLEANUP_INTERVAL - waitDuration - 1000)
			}

			run {
				// this should not trigger a cache cleanup
				val response = client.getMockStringBlocking(url1)
				assertEquals("#1", response)
			}

			// sanity check: test should fail if we add 1000ms instead of subtracting
			skipTime(CacheManager.LAST_ACCESS_IMMUNITY_TIMESPAN - 1000)

			runAndWaitForCacheCleanup {
				// this request should NOT remove the cached response from above
				val response = client.getMockStringBlocking(url2)
				assertEquals("#2", response)
			}

			run {
				// we should get the cached response here
				val response = client.getMockStringBlocking(url1)
				assertEquals("#1", response)
			}
		}
	}

	@Test
	fun autoCleanup_removedLRU() = runTest {
		withServer { number, request ->
			respond(
				content = "#$number",
				headers = headers {
					header(HttpHeaders.Expires, GMTDate(now() + 1.hours.inWholeMilliseconds).toHttpDateString())
				}
			)
		}.testUbiquache(maxCacheSize = 1) { client ->
			val url1 = "http://test/1"
			val url2 = "http://test/2"

			runAndWaitForCacheCleanup {
				val response = client.getMockStringBlocking(url1)
				assertEquals("#1", response)
			}.also { waitDuration ->
				skipTime((10 * 60 * 1000 - waitDuration).coerceAtLeast(0))
			}

			run {
				// this request should should be answered directly from the cache
				val response = client.getMockStringBlocking(url1)
				assertEquals("#1", response)
			}

			skipTimeToNextCacheCleanup()

			runAndWaitForCacheCleanup {
				// this request should remove the cached response from above
				val response = client.getMockStringBlocking(url2)
				assertEquals("#2", response)
			}

			run {
				// cached response has been removed: we should make a new request to the server
				val response = client.getMockStringBlocking(url1)
				assertEquals("#3", response)
			}
		}
	}

	@Test
	fun autoCleanup_keepETag() = runTest {
		withServer { number, request ->
			respond(
				content = "#$number",
				headers = headers {
					header(HttpHeaders.ETag, "asdf")
				}
			)
		}.testUbiquache { client ->
			val url1 = "http://test/1"
			val url2 = "http://test/2"

			run {
				val response = client.getMockStringBlocking(url1)
				assertEquals("#1", response)
			}

			skipTimeToNextCacheCleanup()

			runAndWaitForCacheCleanup {
				// this request should not remove the cached response from above
				val response = client.getMockStringBlocking(url2)
				assertEquals("#2", response)
			}

			run {
				// we should get the cached response here, not an unexpected 304
				val response = client.getMockStringBlocking(url1)
				assertEquals("#3", response)
			}
		}
	}

}
