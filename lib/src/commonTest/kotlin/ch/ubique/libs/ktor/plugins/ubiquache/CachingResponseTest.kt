package ch.ubique.libs.ktor.plugins.ubiquache

import ch.ubique.libs.ktor.*
import ch.ubique.libs.ktor.common.now
import ch.ubique.libs.ktor.common.skipTime
import ch.ubique.libs.ktor.http.*
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headers
import io.ktor.util.date.GMTDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail

class CachingResponseTest {

	private val expiresHeaders = listOf(HttpHeaders.Expires, HttpHeaders.XAmzMetaBestBefore, HttpHeaders.XMsMetaBestbefore)
	private val nextRefreshHeaders = listOf(HttpHeaders.XNextRefresh, HttpHeaders.XAmzMetaNextRefresh, HttpHeaders.XMsMetaNextrefresh)
	private val expiresRefreshCombinations = expiresHeaders.flatMap { expiresHeader ->
		nextRefreshHeaders.map { nextRefreshHeader ->
			expiresHeader to nextRefreshHeader
		}
	}

	@Test
	fun expires_valid() {
		for (expiresHeader in expiresHeaders) {
			withServer { number, request ->
				when (number) {
					1 -> respond(
						content = "novzudfg",
						headers = headers {
							header(expiresHeader, GMTDate(now() + 10000L).toHttpDateString())
						}
					)
					else -> fail("Unexpected request number: $number")
				}
			}.testUbiquache { client ->
				run {
					val response = client.getMockResponseBlocking()
					assertEquals("novzudfg", response.bodyAsTextBlocking())
					assertEquals(1, requestHistory.size)
				}
				run {
					val response = client.getMockResponseBlocking()
					assertEquals("novzudfg", response.bodyAsTextBlocking())
					assertEquals(1, requestHistory.size)
				}
				run {
					val response = client.getMockResponseBlocking()
					assertEquals("novzudfg", response.bodyAsTextBlocking())
					assertEquals(1, requestHistory.size)
				}
			}
		}
	}

	@Test
	fun expires_expired() {
		for (expiresHeader in expiresHeaders) {
			withServer { number, request ->
				when (number) {
					1 -> respond(
						content = "opqientd",
						headers = headers {
							header(expiresHeader, GMTDate(now() - 10000L).toHttpDateString())
						}
					)
					2 -> respond(
						content = "oenfispq",
						headers = headers {
							header(expiresHeader, GMTDate(now() + 10000L).toHttpDateString())
						}
					)
					else -> fail("Unexpected request number: $number")
				}
			}.testUbiquache { client ->
				run {
					val response = client.getMockResponseBlocking()
					assertEquals("opqientd", response.bodyAsTextBlocking())
					assertEquals(1, requestHistory.size)
				}
				skipTime(1000)
				run {
					val response = client.getMockResponseBlocking()
					assertEquals("oenfispq", response.bodyAsTextBlocking())
					assertEquals(2, requestHistory.size)
				}
			}
		}
	}

	@Test
	fun maxage_valid() {
		withServer { number, request ->
			when (number) {
				1 -> respond(
					content = "nsuqkepc",
					headers = headers {
						header(HttpHeaders.CacheControl, "max-age=10")
					}
				)
				else -> fail("Unexpected request number: $number")
			}
		}.testUbiquache { client ->
			run {
				val response = client.getMockResponseBlocking()
				assertEquals("nsuqkepc", response.bodyAsTextBlocking())
				assertEquals(1, requestHistory.size)
			}
			skipTime(500)
			run {
				val response = client.getMockResponseBlocking()
				assertEquals("nsuqkepc", response.bodyAsTextBlocking())
				assertEquals(1, requestHistory.size)
			}
		}
	}

	@Test
	fun maxage_expired() {
		withServer { number, request ->
			when (number) {
				1 -> respond(
					content = "svdporge",
					headers = headers {
						header(HttpHeaders.CacheControl, "max-age=1")
					}
				)
				2 -> respond(
					content = "xcoijtlw",
					headers = headers {
						header(HttpHeaders.CacheControl, "max-age=1")
					}
				)
				else -> fail("Unexpected request number: $number")
			}
		}.testUbiquache { client ->
			run {
				val response = client.getMockResponseBlocking()
				assertEquals("svdporge", response.bodyAsTextBlocking())
				assertEquals(1, requestHistory.size)
			}
			skipTime(2000)
			run {
				val response = client.getMockResponseBlocking()
				assertEquals("xcoijtlw", response.bodyAsTextBlocking())
				assertEquals(2, requestHistory.size)
			}
		}
	}

	@Test
	fun maxage_expired_with_not_modified() {
		withServer { number, request ->
			when (number) {
				1 -> {
					assertNull(request.headers[HttpHeaders.IfNoneMatch])
					respond(
						content = "cmrqnfpi",
						headers = headers {
							header(HttpHeaders.CacheControl, "max-age=1")
							header(HttpHeaders.ETag, "24928194835")
						}
					)
				}
				2 -> {
					assertEquals("24928194835", request.headers[HttpHeaders.IfNoneMatch])
					respond(
						content = "",
						status = HttpStatusCode.NotModified,
						headers = headers {
							header(HttpHeaders.CacheControl, "max-age=2")
							header(HttpHeaders.ETag, "24928194835")
						}
					)
				}
				3 -> {
					assertEquals("24928194835", request.headers[HttpHeaders.IfNoneMatch])
					respond(
						content = "",
						status = HttpStatusCode.NotModified,
						headers = headers {
							header(HttpHeaders.CacheControl, "max-age=2")
							header(HttpHeaders.ETag, "24928194835")
						}
					)
				}
				else -> fail("Unexpected request number: $number")
			}
		}.testUbiquache { client ->
			run {
				val response = client.getMockResponseBlocking()
				assertEquals("cmrqnfpi", response.bodyAsTextBlocking())
				assertEquals(1, requestHistory.size)
			}
			skipTime(1500)
			run {
				val response = client.getMockResponseBlocking()
				assertEquals("cmrqnfpi", response.bodyAsTextBlocking())
				assertEquals(2, requestHistory.size)
			}
			skipTime(1500)
			run {
				val response = client.getMockResponseBlocking()
				assertEquals("cmrqnfpi", response.bodyAsTextBlocking())
				assertEquals(2, requestHistory.size)
			}
			skipTime(1500)
			run {
				val response = client.getMockResponseBlocking()
				assertEquals("cmrqnfpi", response.bodyAsTextBlocking())
				assertEquals(3, requestHistory.size)
			}
		}
	}

	@Test
	fun nextRefresh_fresh() {
		for ((expiresHeader, nextRefreshHeader) in expiresRefreshCombinations) {
			withServer { number, request ->
				when (number) {
					1 -> respond(
						content = "asdelsdd",
						headers = headers {
							header(expiresHeader, GMTDate(now() + 10000L).toHttpDateString())
							header(nextRefreshHeader, GMTDate(now() + 5000L).toHttpDateString())
						}
					)
					else -> fail("Unexpected request number: $number")
				}
			}.testUbiquache { client ->
				run {
					val response = client.getMockResponseBlocking()
					assertEquals("asdelsdd", response.bodyAsTextBlocking())
					assertEquals(1, requestHistory.size)
				}
				run {
					val response = client.getMockResponseBlocking()
					assertEquals("asdelsdd", response.bodyAsTextBlocking())
					assertEquals(1, requestHistory.size)
				}
			}
		}
	}

	@Test
	fun nextRefresh_refreshNotExpired() {
		for ((expiresHeader, nextRefreshHeader) in expiresRefreshCombinations) {
			withServer { number, request ->
				when (number) {
					1 -> respond(
						content = "evntoeiv",
						headers = headers {
							header(expiresHeader, GMTDate(now() + 10000L).toHttpDateString())
							header(nextRefreshHeader, GMTDate(now() + 500L).toHttpDateString())
						}
					)
					2 -> respond(
						content = "caxponvr",
					)
					else -> fail("Unexpected request number: $number")
				}
			}.testUbiquache { client ->
				run {
					val response = client.getMockResponseBlocking()
					assertEquals("evntoeiv", response.bodyAsTextBlocking())
					assertEquals(1, requestHistory.size)
				}
				skipTime(1000)
				run {
					val response = client.getMockResponseBlocking()
					assertEquals("caxponvr", response.bodyAsTextBlocking())
					assertEquals(2, requestHistory.size)
				}
			}
		}
	}

	@Test
	fun nextRefresh_refreshNotExpired_withEtag() {
		withServer { number, request ->
			when (number) {
				1 -> {
					assertNull(request.headers[HttpHeaders.IfNoneMatch])
					respond(
						content = "xnuxwnfd",
						headers = headers {
							header(HttpHeaders.Expires, GMTDate(now() + 10000L).toHttpDateString())
							header(HttpHeaders.XNextRefresh, GMTDate(now() + 500L).toHttpDateString())
							header(HttpHeaders.ETag, "8528484542")
						}
					)
				}
				2 -> {
					assertEquals("8528484542", request.headers[HttpHeaders.IfNoneMatch])
					respond(
						content = "",
						status = HttpStatusCode.NotModified,
						headers = headers {
							header(HttpHeaders.Expires, GMTDate(now() + 10000L).toHttpDateString())
							header(HttpHeaders.XNextRefresh, GMTDate(now() + 500L).toHttpDateString())
							header(HttpHeaders.ETag, "8528484542")
						}
					)
				}
				else -> fail("Unexpected request number: $number")
			}
		}.testUbiquache { client ->
			run {
				val response = client.getMockResponseBlocking()
				assertEquals("xnuxwnfd", response.bodyAsTextBlocking())
				assertEquals(1, requestHistory.size)
			}
			skipTime(1000)
			run {
				val response = client.getMockResponseBlocking()
				assertEquals("xnuxwnfd", response.bodyAsTextBlocking())
				assertEquals(2, requestHistory.size)
			}
		}
	}

	@Test
	fun nextRefresh_expired() {
		for ((expiresHeader, nextRefreshHeader) in expiresRefreshCombinations) {
			withServer { number, request ->
				when (number) {
					1 -> respond(
						content = "xenstrzn",
						headers = headers {
							header(expiresHeader, GMTDate(now() + 500L).toHttpDateString())
							header(nextRefreshHeader, GMTDate(now() + 500L).toHttpDateString())
						}
					)
					2 -> respond(
						content = "uvmvdrou",
					)
					else -> fail("Unexpected request number: $number")
				}
			}.testUbiquache { client ->
				run {
					val response = client.getMockResponseBlocking()
					assertEquals("xenstrzn", response.bodyAsTextBlocking())
					assertEquals(1, requestHistory.size)
				}
				skipTime(1000)
				run {
					val response = client.getMockResponseBlocking()
					assertEquals("uvmvdrou", response.bodyAsTextBlocking())
					assertEquals(2, requestHistory.size)
				}
			}
		}
	}

	@Test
	fun nextRefresh_fallbackOnServerError() {
		withServer { number, request ->
			when (number) {
				1 -> respond(
					content = "zdngueni",
					headers = headers {
						header(HttpHeaders.Expires, GMTDate(now() + 10000L).toHttpDateString())
						header(HttpHeaders.XNextRefresh, GMTDate(now() + 500L).toHttpDateString())
					}
				)
				2 -> respond(
					content = "qxciufed",
					status = HttpStatusCode.ServiceUnavailable,
				)
				else -> fail("Unexpected request number: $number")
			}
		}.testUbiquache { client ->
			run {
				val response = client.getMockResponseBlocking()
				assertEquals("zdngueni", response.bodyAsTextBlocking())
				assertEquals(1, requestHistory.size)
			}
			skipTime(1000)
			run {
				val response = client.getMockResponseBlocking()
				assertEquals("zdngueni", response.bodyAsTextBlocking())
				assertEquals(2, requestHistory.size)
			}
		}
	}

	// TODO: continue with nextRefresh_fallbackOnConnectionError()

}
