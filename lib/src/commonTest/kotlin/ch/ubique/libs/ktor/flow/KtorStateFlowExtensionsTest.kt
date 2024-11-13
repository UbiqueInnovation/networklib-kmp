package ch.ubique.libs.ktor.flow

import app.cash.turbine.test
import ch.ubique.libs.ktor.cache.extensions.cacheControl
import ch.ubique.libs.ktor.testUbiquache
import ch.ubique.libs.ktor.withServer
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.request.header
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class KtorStateFlowExtensionsTest {

	@Test
	fun flatMapLatestToKtorStateFlow() = runTest {
		withServer { number, request ->
			respond(
				content = request.headers["X-Input"]!!,
			)
		}.testUbiquache { client ->
			val inputFlow = MutableStateFlow(1)

			val stateFlow = inputFlow.flatMapLatestToKtorStateFlow { input ->
				ktorStateFlow<Int> { cacheControl ->
					client.get("http://test/") {
						header("X-Input", input)
						cacheControl(cacheControl)
					}
				}
			}

			val count = 3

			stateFlow.test {
				for (input in 1..count) {
					inputFlow.update { input }

					val loading = awaitItem()
					assertIs<RequestState.Loading>(loading)

					val result = awaitItem()
					assertIs<RequestState.Result<Int>>(result)
					assertEquals(input, result.data)
				}
			}

			val result = stateFlow.value
			assertIs<RequestState.Result<Int>>(result)
			assertEquals(count, result.data)

			assertEquals(count, responseHistory.size)
		}
	}

	@Test
	fun flatMapLatestToKtorStateFlow_reload() = runTest {
		withServer { number, request ->
			respond(
				content = request.headers["X-Input"]!! + "." + number,
			)
		}.testUbiquache { client ->
			val inputFlow = MutableStateFlow(1)

			val stateFlow = inputFlow.flatMapLatestToKtorStateFlow { input ->
				ktorStateFlow<String> { cacheControl ->
					client.get("http://test/") {
						header("X-Input", input)
						cacheControl(cacheControl)
					}
				}
			}

			stateFlow.test {
				run {
					val loading = awaitItem()
					assertIs<RequestState.Loading>(loading)

					val result = awaitItem()
					assertIs<RequestState.Result<String>>(result)
					assertEquals("1.1", result.data)
				}
				stateFlow.reload()
				run {
					val loading = awaitItem()
					assertIs<RequestState.Loading>(loading)

					val result = awaitItem()
					assertIs<RequestState.Result<String>>(result)
					assertEquals("1.2", result.data)
				}
				stateFlow.forceReload()
				run {
					val loading = awaitItem()
					assertIs<RequestState.Loading>(loading)

					val result = awaitItem()
					assertIs<RequestState.Result<String>>(result)
					assertEquals("1.3", result.data)
				}
			}

			assertEquals(3, responseHistory.size)
		}
	}

}
