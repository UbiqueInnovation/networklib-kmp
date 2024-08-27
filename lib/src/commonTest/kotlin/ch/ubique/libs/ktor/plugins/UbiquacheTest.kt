package ch.ubique.libs.ktor.plugins

import ch.ubique.libs.ktor.CacheControl
import ch.ubique.libs.ktor.getMockResponseBlocking
import ch.ubique.libs.ktor.getMockStringBlocking
import ch.ubique.libs.ktor.throwIfNotSuccessful
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UbiquacheTest {

	@Test
	fun `uncached request`() {
		val mockEngine = MockEngine { request ->
			respond(
				content = ByteReadChannel("body"),
				status = HttpStatusCode.OK,
				headers = headersOf(HttpHeaders.ContentType, "text/plain")
			)
		}

		val client = HttpClient(mockEngine) {
			install(Ubiquache)
		}

		val result = client.getMockStringBlocking()

		assertEquals("body", result)
	}

	@Test
	fun `only-if-cached`() {
		val mockEngine = MockEngine { request ->
			respond(
				content = ByteReadChannel("body"),
				status = HttpStatusCode.OK
			)
		}

		val client = HttpClient(mockEngine) {
			install(Ubiquache)
		}

		val result = client.getMockResponseBlocking {
			header(HttpHeaders.CacheControl, CacheControl.ONLY_IF_CACHED)
		}

		assertEquals(HttpStatusCode.GatewayTimeout.value, result.status.value)
	}

	@Test
	fun `only-if-cached-2`() {
		val mockEngine = MockEngine { request ->
			respond(
				content = ByteReadChannel("body"),
				status = HttpStatusCode.OK
			)
		}

		val client = HttpClient(mockEngine) {
			install(Ubiquache)
		}

		val result = client.getMockResponseBlocking {
			header(HttpHeaders.CacheControl, CacheControl.ONLY_IF_CACHED)
		}

		assertFailsWith(ResponseException::class) {
			runBlocking { result.throwIfNotSuccessful() }
		}
	}

}
