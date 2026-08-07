package ch.ubique.libs.ktor.plugins.ubiquache

import ch.ubique.libs.ktor.*
import ch.ubique.libs.ktor.common.now
import ch.ubique.libs.ktor.common.skipTime
import ch.ubique.libs.ktor.http.*
import io.ktor.client.engine.mock.respond
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.request.header
import io.ktor.client.utils.CacheControl
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headers
import io.ktor.util.date.GMTDate
import kotlin.test.*

class CacheMarkerTest {

	@Test
	fun fresh_fetch_is_marked_miss() {
		withServer { number, request ->
			respond(
				content = "fgkzvmqp",
				headers = headers {
					header(HttpHeaders.Expires, GMTDate(now() + 10000L).toHttpDateString())
				}
			)
		}.testUbiquache { client ->
			val response = client.getMockResponseBlocking()
			assertEquals("fgkzvmqp", response.bodyAsTextBlocking())
			assertEquals(CacheControlResult.MISS.value, response.headers[HttpHeaders.XUbiquache])
			assertEquals(1, requestHistory.size)
		}
	}

	@Test
	fun cache_hit_is_marked_hit() {
		withServer { number, request ->
			respond(
				content = "vifutmgb",
				headers = headers {
					header(HttpHeaders.Expires, GMTDate(now() + 10000L).toHttpDateString())
				}
			)
		}.testUbiquache { client ->
			run {
				val response = client.getMockResponseBlocking()
				assertEquals("vifutmgb", response.bodyAsTextBlocking())
				assertEquals(CacheControlResult.MISS.value, response.headers[HttpHeaders.XUbiquache])
			}
			run {
				val response = client.getMockResponseBlocking()
				assertEquals("vifutmgb", response.bodyAsTextBlocking())
				assertEquals(CacheControlResult.HIT.value, response.headers[HttpHeaders.XUbiquache])
				assertEquals(1, requestHistory.size)
			}
		}
	}

	@Test
	fun only_if_cached_hit_contains_hit_and_only_if_cached() {
		withServer { number, request ->
			respond(
				content = "wvofyxno",
				headers = headers {
					header(HttpHeaders.Expires, GMTDate(now() + 10000L).toHttpDateString())
				}
			)
		}.testUbiquache { client ->
			run {
				val response = client.getMockResponseBlocking()
				assertEquals("wvofyxno", response.bodyAsTextBlocking())
			}
			run {
				val response = client.getMockResponseBlocking {
					header(HttpHeaders.CacheControl, CacheControl.ONLY_IF_CACHED)
				}
				assertEquals("wvofyxno", response.bodyAsTextBlocking())
				val marker = response.headers[HttpHeaders.XUbiquache]
				assertNotNull(marker)
				assertTrue(marker.contains(CacheControlResult.HIT.value), "expected marker to contain HIT, was: $marker")
				assertTrue(marker.contains(CacheControl.ONLY_IF_CACHED), "expected marker to contain only-if-cached, was: $marker")
				assertEquals(1, requestHistory.size)
			}
		}
	}

	@Test
	fun not_modified_revalidation_is_marked_hit() {
		withServer { number, request ->
			when (number) {
				1 -> respond(
					content = "pruqxjkv",
					headers = headers {
						header(HttpHeaders.Expires, GMTDate(now() + 1000L).toHttpDateString())
						header(HttpHeaders.ETag, "845839022")
					}
				)
				2 -> respond(
					content = "",
					status = HttpStatusCode.NotModified,
					headers = headers {
						header(HttpHeaders.Expires, GMTDate(now() + 1000L).toHttpDateString())
						header(HttpHeaders.ETag, "845839022")
					}
				)
				else -> fail("Unexpected request number: $number")
			}
		}.testUbiquache { client ->
			run {
				val response = client.getMockResponseBlocking()
				assertEquals("pruqxjkv", response.bodyAsTextBlocking())
				assertEquals(CacheControlResult.MISS.value, response.headers[HttpHeaders.XUbiquache])
			}
			skipTime(1500)
			run {
				val response = client.getMockResponseBlocking()
				assertEquals("pruqxjkv", response.bodyAsTextBlocking())
				assertEquals(CacheControlResult.HIT.value, response.headers[HttpHeaders.XUbiquache])
				assertEquals(2, requestHistory.size)
			}
		}
	}

	@Test
	fun fallback_on_server_error_is_marked_hit() {
		withServer { number, request ->
			when (number) {
				1 -> respond(
					content = "fhrkoesx",
					headers = headers {
						header(HttpHeaders.Expires, GMTDate(now() + 10000L).toHttpDateString())
						header(HttpHeaders.XNextRefresh, GMTDate(now() + 500L).toHttpDateString())
					}
				)
				2 -> respond(
					content = "iydgqsmf",
					status = HttpStatusCode.ServiceUnavailable,
				)
				else -> fail("Unexpected request number: $number")
			}
		}.testUbiquache { client ->
			run {
				val response = client.getMockResponseBlocking()
				assertEquals("fhrkoesx", response.bodyAsTextBlocking())
				assertEquals(CacheControlResult.MISS.value, response.headers[HttpHeaders.XUbiquache])
			}
			skipTime(1000)
			run {
				val response = client.getMockResponseBlocking()
				assertEquals("fhrkoesx", response.bodyAsTextBlocking())
				assertEquals(CacheControlResult.HIT.value, response.headers[HttpHeaders.XUbiquache])
				assertEquals(2, requestHistory.size)
			}
		}
	}

	@Test
	fun fallback_on_connection_error_is_marked_hit() {
		withServer { number, request ->
			when (number) {
				1 -> respond(
					content = "tkjexqld",
					headers = headers {
						header(HttpHeaders.Expires, GMTDate(now() + 10000L).toHttpDateString())
						header(HttpHeaders.XNextRefresh, GMTDate(now() + 500L).toHttpDateString())
					}
				)
				2 -> throw SocketTimeoutException("emulate connection error")
				else -> fail("Unexpected request number: $number")
			}
		}.testUbiquache { client ->
			run {
				val response = client.getMockResponseBlocking()
				assertEquals("tkjexqld", response.bodyAsTextBlocking())
				assertEquals(CacheControlResult.MISS.value, response.headers[HttpHeaders.XUbiquache])
			}
			skipTime(1000)
			run {
				val response = client.getMockResponseBlocking()
				assertEquals("tkjexqld", response.bodyAsTextBlocking())
				assertEquals(CacheControlResult.HIT.value, response.headers[HttpHeaders.XUbiquache])
				assertEquals(1, requestHistory.size) // failed request doesn't count
			}
		}
	}

	@Test
	fun refreshed_with_new_response_is_marked_miss() {
		withServer { number, request ->
			when (number) {
				1 -> respond(
					content = "xvupokif",
					headers = headers {
						header(HttpHeaders.Expires, GMTDate(now() + 1000L).toHttpDateString())
						header(HttpHeaders.ETag, "459284023")
					}
				)
				2 -> respond(
					content = "qotilrsa",
					headers = headers {
						header(HttpHeaders.Expires, GMTDate(now() + 1000L).toHttpDateString())
						header(HttpHeaders.ETag, "991320484")
					}
				)
				else -> fail("Unexpected request number: $number")
			}
		}.testUbiquache { client ->
			run {
				val response = client.getMockResponseBlocking()
				assertEquals("xvupokif", response.bodyAsTextBlocking())
				assertEquals(CacheControlResult.MISS.value, response.headers[HttpHeaders.XUbiquache])
			}
			skipTime(1500)
			run {
				val response = client.getMockResponseBlocking()
				assertEquals("qotilrsa", response.bodyAsTextBlocking())
				assertEquals(CacheControlResult.MISS.value, response.headers[HttpHeaders.XUbiquache])
				assertEquals(2, requestHistory.size)
			}
		}
	}

	@Test
	fun non_cacheable_response_has_no_marker() {
		withServer { number, request ->
			respond(
				content = "kkvusxnj",
			)
		}.testUbiquache { client ->
			val response = client.getMockResponseBlocking()
			assertEquals("kkvusxnj", response.bodyAsTextBlocking())
			assertNull(response.headers[HttpHeaders.XUbiquache])
		}
	}

	@Test
	fun only_if_cached_miss_marks_only_only_if_cached() {
		withServer { number, request ->
			respond(
				content = "cwrpdflo",
			)
		}.testUbiquache { client ->
			val response = client.getMockResponseBlocking {
				header(HttpHeaders.CacheControl, CacheControl.ONLY_IF_CACHED)
			}
			assertEquals(HttpStatusCode.GatewayTimeout, response.status)
			assertEquals(CacheControlValue.ONLY_IF_CACHED.value, response.headers[HttpHeaders.XUbiquache])
			assertEquals(0, requestHistory.size)
		}
	}

}
