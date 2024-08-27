package ch.ubique.libs.ktor.flow

import app.cash.turbine.test
import ch.ubique.libs.ktor.cache.extensions.cacheControl
import ch.ubique.libs.ktor.plugins.Ubiquache
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class KtorStateFlowTest {

	@Test
	fun `single request`() = runTest {
		val mockEngine = MockEngine { request ->
			respond(
				content = ByteReadChannel("body"),
				status = HttpStatusCode.OK
			)
		}

		val client = HttpClient(mockEngine) {
			install(Ubiquache)
		}

		val stateFlow = ktorStateFlow<String> { cacheControl ->
			client.get("http://localhost/") {
				cacheControl(cacheControl)
			}
		}

		stateFlow.test {
			val loading = awaitItem()
			assertIs<RequestState.Loading>(loading)

			val result = awaitItem()
			assertIs<RequestState.Result<String>>(result)
			assertEquals("body", result.data)
		}

		assertEquals(1, mockEngine.responseHistory.size)
	}

}
