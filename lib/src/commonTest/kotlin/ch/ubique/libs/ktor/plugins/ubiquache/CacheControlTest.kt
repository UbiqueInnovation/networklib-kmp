package ch.ubique.libs.ktor.plugins.ubiquache

import ch.ubique.libs.ktor.*
import ch.ubique.libs.ktor.common.now
import ch.ubique.libs.ktor.http.toHttpDateString
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.header
import io.ktor.client.utils.CacheControl
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headers
import io.ktor.util.date.GMTDate
import kotlin.test.Test
import kotlin.test.assertEquals

class CacheControlTest {

	@Test
	fun nocache_emptyCache() {
		for (cacheControl in listOf(CacheControl.NO_CACHE, CacheControl.NO_STORE)) {
			withServer { number, request ->
				respond(
					content = "sdmnvpwo",
					status = HttpStatusCode.OK,
					headers = headers {
						header(HttpHeaders.Expires, GMTDate(now() + 1000L).toHttpDateString())
						header(HttpHeaders.ContentType, "text/plain")
					}
				)
			}.testUbiquache { client ->
				val response = client.getMockResponseBlocking()
				assertEquals("sdmnvpwo", response.bodyAsTextBlocking())
				assertEquals("text/plain", response.headers[HttpHeaders.ContentType])
				assertEquals(1, requestHistory.size)
			}
		}
	}

	@Test
	fun noStore_validCache() {
		withServer { number, request ->
			when (number) {
				1 -> respond(
					content = "oqetiudf",
					status = HttpStatusCode.OK,
					headers = headers {
						header(HttpHeaders.Expires, GMTDate(now() + 10000L).toHttpDateString())
						header(HttpHeaders.ContentType, "text/plain")
					}
				)
				2 -> respond(
					content = "xuwerwee",
					status = HttpStatusCode.OK,
					headers = headers {
						header(HttpHeaders.Expires, GMTDate(now() + 10000L).toHttpDateString())
						header(HttpHeaders.ContentType, "text/plain")
					}
				)
				else -> error("Unexpected request number: $number")
			}
		}.testUbiquache { client ->
			run {
				val response = client.getMockResponseBlocking {
					header(HttpHeaders.CacheControl, CacheControl.NO_STORE)
				}
				assertEquals("oqetiudf", response.bodyAsTextBlocking())
				assertEquals("text/plain", response.headers[HttpHeaders.ContentType])
				assertEquals(1, requestHistory.size)
			}
			run {
				val response = client.getMockResponseBlocking()
				assertEquals("xuwerwee", response.bodyAsTextBlocking())
				assertEquals("text/plain", response.headers[HttpHeaders.ContentType])
				assertEquals(2, requestHistory.size)
			}
			run {
				val response = client.getMockResponseBlocking {
					header(HttpHeaders.CacheControl, CacheControl.NO_STORE)
				}
				assertEquals("xuwerwee", response.bodyAsTextBlocking())
				assertEquals("text/plain", response.headers[HttpHeaders.ContentType])
				assertEquals(2, requestHistory.size)
			}
		}
	}

	@Test
	fun noCache_validCache() {
		withServer { number, request ->
			when (number) {
				1 -> respond(
					content = "ksnfzepa",
					status = HttpStatusCode.OK,
					headers = headers {
						header(HttpHeaders.Expires, GMTDate(now() + 10000L).toHttpDateString())
						header(HttpHeaders.ContentType, "text/plain")
					}
				)
				2 -> respond(
					content = "pqnfudme",
					status = HttpStatusCode.OK,
					headers = headers {
						header(HttpHeaders.Expires, GMTDate(now() + 10000L).toHttpDateString())
						header(HttpHeaders.ContentType, "text/plain")
					}
				)
				else -> error("Unexpected request number: $number")
			}
		}.testUbiquache { client ->
			run {
				val response = client.getMockResponseBlocking {
					header(HttpHeaders.CacheControl, CacheControl.NO_CACHE)
				}
				assertEquals("ksnfzepa", response.bodyAsTextBlocking())
				assertEquals("text/plain", response.headers[HttpHeaders.ContentType])
				assertEquals(1, requestHistory.size)
			}
			run {
				val response = client.getMockResponseBlocking()
				assertEquals("ksnfzepa", response.bodyAsTextBlocking())
				assertEquals("text/plain", response.headers[HttpHeaders.ContentType])
				assertEquals(1, requestHistory.size)
			}
			run {
				val response = client.getMockResponseBlocking {
					header(HttpHeaders.CacheControl, CacheControl.NO_CACHE)
				}
				assertEquals("pqnfudme", response.bodyAsTextBlocking())
				assertEquals("text/plain", response.headers[HttpHeaders.ContentType])
				assertEquals(2, requestHistory.size)
			}
		}
	}

	@Test
	fun cacheOnly_emptyCache() {
		withServer { number, request ->
			respond(
				content = "pcvbjcpo",
				status = HttpStatusCode.OK,
				headers = headers {
					header(HttpHeaders.Expires, GMTDate(now() + 10000L).toHttpDateString())
					header(HttpHeaders.ContentType, "text/plain")
				}
			)
		}.testUbiquache { client ->
			val response = client.getMockResponseBlocking {
				header(HttpHeaders.CacheControl, CacheControl.ONLY_IF_CACHED)
			}
			assertEquals(HttpStatusCode.GatewayTimeout, response.status)
			assertEquals(0, requestHistory.size)
		}
	}

	@Test
	fun cacheOnly_validCache() {
		withServer { number, request ->
			respond(
				content = "bntemrbs",
				status = HttpStatusCode.OK,
				headers = headers {
					header(HttpHeaders.Expires, GMTDate(now() + 10000L).toHttpDateString())
					header(HttpHeaders.ContentType, "text/plain")
				}
			)
		}.testUbiquache { client ->
			run {
				val response = client.getMockResponseBlocking()
				assertEquals("bntemrbs", response.bodyAsTextBlocking())
				assertEquals("text/plain", response.headers[HttpHeaders.ContentType])
				assertEquals(1, requestHistory.size)
			}
			run {
				val response = client.getMockResponseBlocking {
					header(HttpHeaders.CacheControl, CacheControl.ONLY_IF_CACHED)
				}
				assertEquals("bntemrbs", response.bodyAsTextBlocking())
				assertEquals("text/plain", response.headers[HttpHeaders.ContentType])
				assertEquals(1, requestHistory.size)
			}
		}
	}

}
