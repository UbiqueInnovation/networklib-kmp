package ch.ubique.libs.ktor.plugins.acceptlanguage

import ch.ubique.libs.ktor.getMockStringBlocking
import ch.ubique.libs.ktor.plugins.AcceptLanguage
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlin.test.Test
import kotlin.test.assertEquals

class AcceptLanguageTest {

	@Test
	fun staticLanguage() {
		val mockEngine = MockEngine { request ->
			respond(
				content = ByteReadChannel(requireNotNull(request.headers[HttpHeaders.AcceptLanguage])),
				status = HttpStatusCode.OK,
				headers = headersOf(HttpHeaders.ContentType, "text/plain")
			)
		}

		val client = HttpClient(mockEngine) {
			install(AcceptLanguage) {
				language = "de"
			}
		}

		val result = client.getMockStringBlocking()

		assertEquals("de", result)
	}

	@Test
	fun dynamicLanguage() {
		val mockEngine = MockEngine { request ->
			respond(
				content = ByteReadChannel(requireNotNull(request.headers[HttpHeaders.AcceptLanguage])),
				status = HttpStatusCode.OK,
				headers = headersOf(HttpHeaders.ContentType, "text/plain")
			)
		}

		val client = HttpClient(mockEngine) {
			install(AcceptLanguage) {
				languageProvider = { "en" }
			}
		}

		val result = client.getMockStringBlocking()

		assertEquals("en", result)
	}

}
