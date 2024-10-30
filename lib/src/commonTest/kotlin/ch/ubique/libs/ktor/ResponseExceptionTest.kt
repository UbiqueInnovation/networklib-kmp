package ch.ubique.libs.ktor

import ch.ubique.libs.ktor.http.throwIfNotSuccessful
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ResponseExceptionTest {

	@Test
	fun responseException() {
		val mockEngine = MockEngine { request ->
			respond(
				content = ByteReadChannel("body"),
				status = HttpStatusCode.NotFound
			)
		}

		val client = HttpClient(mockEngine)

		val result = client.getMockResponseBlocking()

		val exception = runCatching { runBlocking { result.throwIfNotSuccessful() } }.exceptionOrNull()
		assertNotNull(exception)

		val response = (exception as? ResponseException)?.response
		assertNotNull(response)

		assertEquals(HttpStatusCode.NotFound, response.status)

		val body = runBlocking { response.bodyAsText() }
		assertEquals("body", body)
	}

}
