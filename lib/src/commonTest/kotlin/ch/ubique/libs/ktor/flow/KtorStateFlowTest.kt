package ch.ubique.libs.ktor.flow

import app.cash.turbine.test
import ch.ubique.libs.ktor.cache.extensions.cacheControl
import ch.ubique.libs.ktor.common.now
import ch.ubique.libs.ktor.common.skipTime
import ch.ubique.libs.ktor.header
import ch.ubique.libs.ktor.http.toHttpDateString
import ch.ubique.libs.ktor.testUbiquache
import ch.ubique.libs.ktor.withServer
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import io.ktor.http.headers
import io.ktor.util.date.GMTDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class KtorStateFlowTest {

	@Test
	fun singleRequest() = runTest {
		withServer { number, request ->
			respond(
				content = "body",
			)
		}.testUbiquache { client ->
			val stateFlow = ktorStateFlow<String> { cacheControl ->
				client.get("http://test/") {
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

			assertEquals(1, responseHistory.size)
		}
	}

	@Test
	fun ubiquacheNotApplied() = runTest {
		val mockEngine = MockEngine { request ->
			respond(
				content = "body",
			)
		}

		val client = HttpClient(mockEngine)

		val stateFlow = ktorStateFlow<String> { cacheControl ->
			client.get("http://test/") {
				cacheControl(cacheControl)
			}
		}

		stateFlow.test {
			val loading = awaitItem()
			assertIs<RequestState.Loading>(loading)

			val error = awaitItem()
			assertIs<RequestState.Error>(error)
			assertIs<IllegalStateException>(error.exception)
		}

		assertEquals(1, mockEngine.responseHistory.size)
	}

	@Test
	fun fromCache() = runTest {
		withServer { number, request ->
			respond(
				content = "#$number",
				headers = headers {
					header(HttpHeaders.Expires, GMTDate(now() + 9000L).toHttpDateString())
				}
			)
		}.testUbiquache { client ->
			run {
				val stateFlow = ktorStateFlow<String> { cacheControl ->
					client.get("http://test/") {
						cacheControl(cacheControl)
					}
				}
				stateFlow.test {
					val loading = awaitItem()
					assertIs<RequestState.Loading>(loading)

					val result = awaitItem()
					assertIs<RequestState.Result<String>>(result)
					assertEquals("#1", result.data)
				}
				assertEquals(1, responseHistory.size)
			}
			run {
				val stateFlow = ktorStateFlow<String> { cacheControl ->
					client.get("http://test/") {
						cacheControl(cacheControl)
					}
				}
				stateFlow.test {
					val loading = awaitItem()
					assertIs<RequestState.Loading>(loading)

					val result = awaitItem()
					assertIs<RequestState.Result<String>>(result)
					assertEquals("#1", result.data)
				}
				assertEquals(1, responseHistory.size)
			}
		}
	}

	@Test
	fun expiredCache() = runTest {
		withServer { number, request ->
			respond(
				content = "#$number",
				headers = headers {
					header(HttpHeaders.Expires, GMTDate(now() + 1000L).toHttpDateString())
				}
			)
		}.testUbiquache { client ->
			run {
				val stateFlow = ktorStateFlow<String> { cacheControl ->
					client.get("http://test/") {
						cacheControl(cacheControl)
					}
				}
				stateFlow.test {
					val loading = awaitItem()
					assertIs<RequestState.Loading>(loading)

					val result = awaitItem()
					assertIs<RequestState.Result<String>>(result)
					assertEquals("#1", result.data)
				}
				assertEquals(1, responseHistory.size)
			}
			skipTime(2000)
			run {
				val stateFlow = ktorStateFlow<String> { cacheControl ->
					client.get("http://test/") {
						cacheControl(cacheControl)
					}
				}
				stateFlow.test {
					val loading = awaitItem()
					assertIs<RequestState.Loading>(loading)

					val result = awaitItem()
					assertIs<RequestState.Result<String>>(result)
					assertEquals("#2", result.data)
				}
				assertEquals(2, responseHistory.size)
			}
		}
	}

	@Test
	fun repeatedRequest() = runTest {
		withServer { number, request ->
			respond(
				content = "#$number",
				headers = headers {
					header(HttpHeaders.Expires, GMTDate(now() + 500L).toHttpDateString())
				}
			)
		}.testUbiquache { client ->
			run {
				val stateFlow = ktorStateFlow<String>(defaultRefreshBackoff = 0) { cacheControl ->
					client.get("http://test/") {
						cacheControl(cacheControl)
					}
				}
				stateFlow.test {
					val loading = awaitItem()
					assertIs<RequestState.Loading>(loading)
					run {
						val result = awaitItem()
						assertIs<RequestState.Result<String>>(result)
						assertEquals("#1", result.data)
					}
					run {
						val result = awaitItem()
						assertIs<RequestState.Result<String>>(result)
						assertEquals("#2", result.data)
					}
				}
				assertEquals(2, responseHistory.size)
			}
		}
	}

	@Test
	fun forceReload() = runTest {
		withServer { number, request ->
			respond(
				content = "#$number",
				headers = headers {
					header(HttpHeaders.Expires, GMTDate(now() + 9000L).toHttpDateString())
				}
			)
		}.testUbiquache { client ->
			run {
				val stateFlow = ktorStateFlow<String>(defaultRefreshBackoff = 0) { cacheControl ->
					client.get("http://test/") {
						cacheControl(cacheControl)
					}
				}
				stateFlow.test {
					run {
						val loading = awaitItem()
						assertIs<RequestState.Loading>(loading)

						val result = awaitItem()
						assertIs<RequestState.Result<String>>(result)
						assertEquals("#1", result.data)
					}

					stateFlow.forceReload()

					run {
						val loading = awaitItem()
						assertIs<RequestState.Loading>(loading)

						val result = awaitItem()
						assertIs<RequestState.Result<String>>(result)
						assertEquals("#2", result.data)
					}
				}
				assertEquals(2, responseHistory.size)
			}
		}
	}

	@Test
	fun reload_cached() = runTest {
		withServer { number, request ->
			respond(
				content = "#$number",
				headers = headers {
					header(HttpHeaders.Expires, GMTDate(now() + 9000L).toHttpDateString())
				}
			)
		}.testUbiquache { client ->
			run {
				val stateFlow = ktorStateFlow<String>(defaultRefreshBackoff = 0) { cacheControl ->
					client.get("http://test/") {
						cacheControl(cacheControl)
					}
				}
				stateFlow.test {
					run {
						val loading = awaitItem()
						assertIs<RequestState.Loading>(loading)

						val result = awaitItem()
						assertIs<RequestState.Result<String>>(result)
						assertEquals("#1", result.data)
					}

					stateFlow.reload()

					run {
						val loading = awaitItem()
						assertIs<RequestState.Loading>(loading)

						val result = awaitItem()
						assertIs<RequestState.Result<String>>(result)
						assertEquals("#1", result.data)
					}
				}
				assertEquals(1, responseHistory.size)
			}
		}
	}

	@Test
	fun reload_changingParameter() = runTest {
		withServer { number, request ->
			val param = request.url.parameters["param"] ?: fail()
			respond(
				content = "p$param",
				headers = headers {
					header(HttpHeaders.Expires, GMTDate(now() + 9000L).toHttpDateString())
				}
			)
		}.testUbiquache { client ->
			run {
				var param = 1
				val stateFlow = ktorStateFlow<String>(defaultRefreshBackoff = 0) { cacheControl ->
					client.get("http://test/?param=$param") {
						cacheControl(cacheControl)
					}
				}
				stateFlow.test {
					run {
						val loading = awaitItem()
						assertIs<RequestState.Loading>(loading)

						val result = awaitItem()
						assertIs<RequestState.Result<String>>(result)
						assertEquals("p1", result.data)
					}

					param++
					stateFlow.reload()

					run {
						val loading = awaitItem()
						assertIs<RequestState.Loading>(loading)

						val result = awaitItem()
						assertIs<RequestState.Result<String>>(result)
						assertEquals("p2", result.data)
					}
				}
				assertEquals(2, responseHistory.size)
			}
		}
	}

	@Test
	fun connectionError() = runTest {
		var triggeredConnectionException = false
		withServer { number, request ->
			triggeredConnectionException = true
			throw SocketTimeoutException("emulate connection error")
		}.testUbiquache { client ->
			val stateFlow = ktorStateFlow<String> { cacheControl ->
				client.get("http://test/") {
					cacheControl(cacheControl)
				}
			}

			stateFlow.test {
				val loading = awaitItem()
				assertIs<RequestState.Loading>(loading)

				val result = awaitItem()
				assertIs<RequestState.Error>(result)
				assertIs<SocketTimeoutException>(result.exception)
			}

			assertTrue(triggeredConnectionException)
			assertEquals(0, responseHistory.size)
		}
	}

	@Test
	fun postRequest() = runTest {
		withServer { number, request ->
			respond(
				content = "post",
			)
		}.testUbiquache { client ->
			val stateFlow = ktorStateFlow<String> { cacheControl ->
				client.post("http://test/") {
					cacheControl(cacheControl)
				}
			}

			stateFlow.test {
				val loading = awaitItem()
				assertIs<RequestState.Loading>(loading)

				val result = awaitItem()
				assertIs<RequestState.Result<String>>(result)
				assertEquals("post", result.data)
			}

			assertEquals(1, responseHistory.size)
		}
	}

	@Test
	fun initialValue() = runTest {
		withServer { _, _ ->
			fail("Should not be called")
		}.testUbiquache { client ->
			val stateFlow = ktorStateFlow<String> { cacheControl ->
				client.get("http://test/") {
					fail("Should not be called")
				}
			}

			assertIs<RequestState.Loading>(stateFlow.value)
		}
	}

	@Test
	fun resetAfterUnsubscribe() = runBlocking {
		// runTest() doesn't work, as the KtorStateFlow cleanup job is completely independent of the test scope
		withServer { _, _ ->
			respondOk()
		}.testUbiquache { client ->
			val stateFlow = ktorStateFlow<String> { cacheControl ->
				client.get("http://test/") {
					cacheControl(cacheControl)
				}
			}

			stateFlow.test {
				val loading = awaitItem()
				assertIs<RequestState.Loading>(loading)

				val result = awaitItem()
				assertIs<RequestState.Result<String>>(result)
			}

			delay(CANCELLATION_DELAY + 500)

			assertIs<RequestState.Loading>(stateFlow.value)

			assertEquals(1, responseHistory.size)
		}
	}

}
