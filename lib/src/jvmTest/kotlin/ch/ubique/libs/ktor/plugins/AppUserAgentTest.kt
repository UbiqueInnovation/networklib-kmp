package ch.ubique.libs.ktor.plugins

import ch.ubique.libs.ktor.getMockStringBlocking
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlin.test.Test
import kotlin.test.assertEquals

class AppUserAgentTest {

	@Test
	fun `test AppUserAgent`() {
		val mockEngine = MockEngine { request ->
			respond(
				content = ByteReadChannel(request.headers[HttpHeaders.UserAgent].toString()),
				status = HttpStatusCode.OK,
				headers = headersOf(HttpHeaders.ContentType, "text/plain")
			)
		}

		val client = HttpClient(mockEngine) {
			AppUserAgent()
		}

		val result = client.getMockStringBlocking()

		assertEquals(AppUserAgentProvider.getUserAgentString(), result)
	}

}
