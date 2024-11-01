package ch.ubique.libs.ktor.plugins.ubiquache

import ch.ubique.libs.ktor.*
import ch.ubique.libs.ktor.common.now
import ch.ubique.libs.ktor.http.toHttpDateString
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headers
import io.ktor.util.date.GMTDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.fail

class ErrorHandlingTest {

	@Test
	fun networkError() {
		withServer { number, request ->
			throw SocketTimeoutException("emulate connection error")
		}.testUbiquache { client ->
			assertFailsWith(SocketTimeoutException::class) {
				client.getMockResponseBlocking()
			}
		}
	}

	@Test
	fun serverError() {
		withServer { number, request ->
			respondError(HttpStatusCode.InternalServerError)
		}.testUbiquache { client ->
			val response = client.getMockResponseBlocking()
			assertEquals(HttpStatusCode.InternalServerError, response.status)
			assertEquals(1, requestHistory.size)
		}
	}

	@Test
	fun serverError_cache() {
		withServer { number, request ->
			when (number) {
				1 -> respond(
					content = "biqedfpd",
					status = HttpStatusCode.InternalServerError,
					headers = headers {
						header(HttpHeaders.Expires, GMTDate(now() + 1000L).toHttpDateString())
					}
				)
				2 -> respond(
					content = "ocnduees",
					headers = headers {
						header(HttpHeaders.Expires, GMTDate(now() + 1000L).toHttpDateString())
					}
				)
				else -> fail("Unexpected request number: $number")
			}
		}.testUbiquache { client ->
			run {
				val response = client.getMockResponseBlocking()
				assertEquals(HttpStatusCode.InternalServerError, response.status)
				assertEquals(1, requestHistory.size)
			}
			run {
				val response = client.getMockResponseBlocking()
				assertEquals(HttpStatusCode.OK, response.status)
				assertEquals("ocnduees", response.bodyAsTextBlocking())
				assertEquals(2, requestHistory.size)
			}
		}
	}

	@Test
	fun notFound_cache() {
		withServer { number, request ->
			when (number) {
				1 -> respond(
					content = "xmspggth",
					status = HttpStatusCode.NotFound,
					headers = headers {
						header(HttpHeaders.Expires, GMTDate(now() + 1000L).toHttpDateString())
					}
				)
				2 -> respond(
					content = "gknufskr",
					headers = headers {
						header(HttpHeaders.Expires, GMTDate(now() + 1000L).toHttpDateString())
					}
				)
				else -> fail("Unexpected request number: $number")
			}
		}.testUbiquache { client ->
			run {
				val response = client.getMockResponseBlocking()
				assertEquals(HttpStatusCode.NotFound, response.status)
				assertEquals(1, requestHistory.size)
			}
			run {
				val response = client.getMockResponseBlocking()
				assertEquals(HttpStatusCode.OK, response.status)
				assertEquals("gknufskr", response.bodyAsTextBlocking())
				assertEquals(2, requestHistory.size)
			}
		}
	}

	@Test
	fun notFound_cacheClear() {
		withServer { number, request ->
			when (number) {
				1 -> respond(
					content = "xmspggth",
					headers = headers {
						header(HttpHeaders.Expires, GMTDate(now() + -1L).toHttpDateString())
					}
				)
				2 -> respond(
					content = "gknufskr",
					status = HttpStatusCode.NotFound,
				)
				3 -> respond(
					content = "mapqlrmg",
					headers = headers {
						header(HttpHeaders.Expires, GMTDate(now() + 1000L).toHttpDateString())
					}
				)
				else -> fail("Unexpected request number: $number")
			}
		}.testUbiquache { client ->
			run {
				val response = client.getMockResponseBlocking()
				assertEquals(HttpStatusCode.OK, response.status)
				assertEquals("xmspggth", response.bodyAsTextBlocking())
				assertEquals(1, requestHistory.size)
			}
			run {
				val response = client.getMockResponseBlocking()
				assertEquals(HttpStatusCode.NotFound, response.status)
				assertEquals(2, requestHistory.size)
			}
			run {
				val response = client.getMockResponseBlocking()
				assertEquals(HttpStatusCode.OK, response.status)
				assertEquals("mapqlrmg", response.bodyAsTextBlocking())
				assertEquals(3, requestHistory.size)
			}
		}
	}

	@Test
	fun unexpected_notModified() {
		withServer { number, request ->
			respondError(HttpStatusCode.NotModified)
		}.testUbiquache { client ->
			val response = client.getMockResponseBlocking()
			assertEquals(HttpStatusCode.NotModified, response.status)
			assertEquals(1, requestHistory.size)
		}
	}

}
