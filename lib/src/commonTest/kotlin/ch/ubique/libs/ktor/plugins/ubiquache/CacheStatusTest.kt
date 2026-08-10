package ch.ubique.libs.ktor.plugins.ubiquache

import ch.ubique.libs.ktor.*
import ch.ubique.libs.ktor.cache.extensions.isCacheHit
import ch.ubique.libs.ktor.cache.extensions.isReadFromCache
import ch.ubique.libs.ktor.common.now
import ch.ubique.libs.ktor.common.skipTime
import ch.ubique.libs.ktor.http.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.respond
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.request.header
import io.ktor.client.utils.CacheControl
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headers
import io.ktor.util.date.GMTDate
import kotlin.test.*

class CacheStatusTest {

	@Test
	fun cacheMissAndHitAreReported() {
		withServer { _, _ ->
			respond(
				content = "cached",
				headers = headers {
					header(HttpHeaders.Expires, GMTDate(now() + 10_000).toHttpDateString())
				},
			)
		}.testUbiquache { client ->
			val miss = client.getMockResponseBlocking()
			assertCacheStatus(miss, "; fwd=miss; stored")
			assertFalse(miss.isCacheHit())
			assertFalse(miss.isReadFromCache())

			val hit = client.getMockResponseBlocking()
			assertCacheStatus(hit, "; hit")
			assertTrue(hit.isCacheHit())
			assertTrue(hit.isReadFromCache())
		}
	}

	@Test
	fun requestNoCacheIsReportedAsForwardedRequest() {
		withServer { _, _ ->
			respond(
				content = "cached",
				headers = headers {
					header(HttpHeaders.Expires, GMTDate(now() + 10_000).toHttpDateString())
				},
			)
		}.testUbiquache { client ->
			client.getMockResponseBlocking()
			val response = client.getMockResponseBlocking {
				header(HttpHeaders.CacheControl, CacheControl.NO_CACHE)
			}
			assertCacheStatus(response, "; fwd=request; stored")
			assertFalse(response.isReadFromCache())
		}
	}

	@Test
	fun validationIsReportedAsStaleForwardWithStatus() {
		withServer { number, _ ->
			when (number) {
				1 -> respond(
					content = "cached",
					headers = headers {
						header(HttpHeaders.Expires, GMTDate(now() + 1_000).toHttpDateString())
						header(HttpHeaders.ETag, "etag")
					},
				)
				2 -> respond(
					content = "",
					status = HttpStatusCode.NotModified,
					headers = headers {
						header(HttpHeaders.Expires, GMTDate(now() + 1_000).toHttpDateString())
						header(HttpHeaders.ETag, "etag")
					},
				)
				else -> error("Unexpected request number: $number")
			}
		}.testUbiquache { client ->
			client.getMockResponseBlocking()
			skipTime(1_500)
			val response = client.getMockResponseBlocking()
			assertCacheStatus(response, "; fwd=stale; fwd-status=304")
			assertFalse(response.isCacheHit())
			assertTrue(response.isReadFromCache())
		}
	}

	@Test
	fun nonCacheableResponseHasNoCacheStatus() {
		withServer { _, _ ->
			respond(
				content = "not cached",
				headers = headers {
					header(HttpHeaders.CacheControl, CacheControl.NO_STORE)
				},
			)
		}.testUbiquache { client ->
			val response = client.getMockResponseBlocking()
			assertFalse(response.headers.contains(HttpHeaders.CacheStatus))
			assertFalse(response.isCacheHit())
			assertFalse(response.isReadFromCache())
		}
	}

	@Test
	fun fallbackToCacheAfterAServerErrorIsReportedAsMissForward() {
		withServer { number, _ ->
			when (number) {
				1 -> respond(
					content = "cached",
					headers = headers {
						header(HttpHeaders.Expires, GMTDate(now() + 10_000).toHttpDateString())
						header(HttpHeaders.XNextRefresh, GMTDate(now() + 500).toHttpDateString())
					},
				)
				2 -> respond(content = "", status = HttpStatusCode.ServiceUnavailable)
				else -> error("Unexpected request number: $number")
			}
		}.testUbiquache { client ->
			client.getMockResponseBlocking()
			skipTime(1_000)
			val response = client.getMockResponseBlocking()
			assertCacheStatus(response, "; fwd=miss")
			assertTrue(response.isReadFromCache())
		}
	}

	@Test
	fun fallbackToCacheAfterANetworkErrorIsReportedAsMissForward() {
		withServer { number, _ ->
			when (number) {
				1 -> respond(
					content = "cached",
					headers = headers {
						header(HttpHeaders.Expires, GMTDate(now() + 10_000).toHttpDateString())
						header(HttpHeaders.XNextRefresh, GMTDate(now() + 500).toHttpDateString())
					},
				)
				2 -> throw SocketTimeoutException("emulate connection error")
				else -> error("Unexpected request number: $number")
			}
		}.testUbiquache { client ->
			client.getMockResponseBlocking()
			skipTime(1_000)
			val response = client.getMockResponseBlocking()
			assertCacheStatus(response, "; fwd=miss")
			assertTrue(response.isReadFromCache())
		}
	}

	@Test
	fun upstreamCacheStatusIsPreserved() {
		withServer { _, _ ->
			respond(
				content = "cached",
				headers = headers {
					header(HttpHeaders.CacheStatus, "\"upstream\"; hit")
					header(HttpHeaders.Expires, GMTDate(now() + 10_000).toHttpDateString())
				},
			)
		}.testUbiquache { client ->
			val response = client.getMockResponseBlocking()
			val cacheStatus = response.headers.getAll(HttpHeaders.CacheStatus).orEmpty()
			assertTrue("\"upstream\"; hit" in cacheStatus)
			assertTrue(cacheStatus.any { it.endsWith("; fwd=miss; stored") })
			assertFalse(response.isCacheHit())
			assertFalse(response.isReadFromCache())
		}
	}

	@Test
	fun onlyIfCachedMissHasNoCacheStatus() {
		withServer { _, _ -> error("Network request should not be made") }
			.testUbiquache { client ->
				val response = client.getMockResponseBlocking {
					header(HttpHeaders.CacheControl, CacheControl.ONLY_IF_CACHED)
				}
				assertEquals(HttpStatusCode.GatewayTimeout, response.status)
				assertFalse(response.headers.contains(HttpHeaders.CacheStatus))
				assertFalse(response.isCacheHit())
				assertFalse(response.isReadFromCache())
			}
	}

	@Test
	fun isCacheHitUsesProvidedCacheName() {
		val engine = withServer { _, _ ->
			respond(
				content = "cached",
				headers = headers {
					header(HttpHeaders.CacheStatus, "\"custom\"; hit")
				},
			)
		}
		val client = HttpClient(engine)
		try {
			val response = client.getMockResponseBlocking()
			assertFalse(response.isCacheHit())
			assertTrue(response.isCacheHit("custom"))
			assertFalse(response.isReadFromCache())
			assertTrue(response.isReadFromCache("custom"))
		} finally {
			client.close()
		}
	}

	@Test
	fun cacheStatusHelpersAcceptTokenCacheNames() {
		val engine = withServer { _, _ ->
			respond(
				content = "cached",
				headers = headers {
					header(HttpHeaders.CacheStatus, "custom; hit")
				},
			)
		}
		val client = HttpClient(engine)
		try {
			val response = client.getMockResponseBlocking()
			assertTrue(response.isCacheHit("custom"))
			assertTrue(response.isReadFromCache("custom"))
		} finally {
			client.close()
		}
	}

	private fun assertCacheStatus(response: io.ktor.client.statement.HttpResponse, expectedSuffix: String) {
		val cacheStatus = response.headers.getAll(HttpHeaders.CacheStatus).orEmpty()
		assertTrue(cacheStatus.any { it == "\"ubiquache\"$expectedSuffix" })
	}
}
