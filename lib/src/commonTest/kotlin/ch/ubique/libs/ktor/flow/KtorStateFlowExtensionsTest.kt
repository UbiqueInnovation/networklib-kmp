package ch.ubique.libs.ktor.flow

import app.cash.turbine.test
import ch.ubique.libs.ktor.cache.extensions.cacheControl
import ch.ubique.libs.ktor.testUbiquache
import ch.ubique.libs.ktor.withServer
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.request.header
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.fail

class KtorStateFlowExtensionsTest {

	@Test
	fun asStateFlow() = runTest {
		val backingFlow = MutableStateFlow<Int?>(null)
		val ktorFlow = object : KtorStateFlow<Int?>(backingFlow) {
			override fun reload() = fail("Should not be called")
			override fun forceReload() = fail("Should not be called")
		}
		val stateFlow: StateFlow<Int?> = ktorFlow.asStateFlow()

		assertFalse(stateFlow is KtorStateFlow<*>)
		assertFalse(stateFlow is MutableStateFlow<*>)
		assertNull(stateFlow.value)
		assertEquals(listOf(null), stateFlow.replayCache)

		backingFlow.value = 1
		assertEquals(1, stateFlow.value)
		assertEquals(listOf(1), stateFlow.replayCache)
		assertEquals(0, backingFlow.subscriptionCount.value)

		stateFlow.test {
			assertEquals(1, awaitItem())
			assertEquals(1, backingFlow.subscriptionCount.value)

			backingFlow.value = 2
			assertEquals(2, awaitItem())
			assertEquals(2, stateFlow.value)
			assertEquals(listOf(2), stateFlow.replayCache)

			backingFlow.value = null
			assertNull(awaitItem())
			assertNull(stateFlow.value)
			assertEquals(listOf(null), stateFlow.replayCache)

			cancelAndIgnoreRemainingEvents()
		}

		assertEquals(0, backingFlow.subscriptionCount.value)
	}

	@Test
	fun flatMapLatestToKtorStateFlow() = runTest {
		withServer { number, request ->
			respond(
				content = request.headers["X-Input"]!!,
			)
		}.testUbiquache { client ->
			val inputFlow = MutableStateFlow(1)

			val stateFlow = inputFlow.flatMapLatestToKtorStateFlow(backgroundScope) { input ->
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
	fun flatMapLatestToKtorStateFlow_initialValue() = runTest {
		withServer { _, _ ->
			fail("Should not be called")
		}.testUbiquache { client ->
			val inputFlow = flow<Any> { fail("Should not be called") }

			val stateFlow = inputFlow.flatMapLatestToKtorStateFlow(backgroundScope) { _ ->
				ktorStateFlow<Any> { _ ->
					fail("Should not be called")
				}
			}

			assertIs<RequestState.Loading>(stateFlow.value)
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

			val stateFlow = inputFlow.flatMapLatestToKtorStateFlow(backgroundScope) { input ->
				ktorStateFlow<String> { cacheControl ->
					client.get("http://test/") {
						header("X-Input", input)
						cacheControl(cacheControl)
					}
				}
			}

			stateFlow.asStateFlow().test {
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

	@Test
	fun flatMapLatestToKtorStateFlow_resetAfterUnsubscribe() = runTest {
		withServer { number, request ->
			respond(
				content = "#$number",
			)
		}.testUbiquache { client ->
			val inputFlow = MutableStateFlow(1)

			val stateFlow = inputFlow.flatMapLatestToKtorStateFlow(backgroundScope) { input ->
				ktorStateFlow<String> { cacheControl ->
					client.get("http://test/") {
						cacheControl(cacheControl)
					}
				}
			}

			stateFlow.test {
				val loading = awaitItem()
				assertIs<RequestState.Loading>(loading)

				val result = awaitItem()
				assertIs<RequestState.Result<String>>(result)
				assertEquals("#1", result.data)
			}

			delay(CANCELLATION_DELAY + 500)

			assertIs<RequestState.Loading>(stateFlow.value)

			assertEquals(1, responseHistory.size)
		}
	}

}
