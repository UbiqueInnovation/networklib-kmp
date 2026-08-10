package ch.ubique.libs.ktor.flow

import app.cash.turbine.test
import ch.ubique.libs.ktor.cache.extensions.cacheControl
import ch.ubique.libs.ktor.cache.extensions.isCacheHit
import ch.ubique.libs.ktor.common.now
import ch.ubique.libs.ktor.header
import ch.ubique.libs.ktor.http.toHttpDateString
import ch.ubique.libs.ktor.testUbiquache
import ch.ubique.libs.ktor.withServer
import io.ktor.client.call.body
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.headers
import io.ktor.util.date.GMTDate
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class KtorStateFlowWithResultTransformTest {

	private data class ResponseResult(
		val body: String,
		val eTag: String?,
	)

	private data class CacheHitResponseResult(
		val body: String,
		val isCacheHit: Boolean,
	)

	@Test
	fun transformedResponse() = runTest {
		withServer { number, request ->
			respond(
				content = "body",
				headers = headers {
					header(HttpHeaders.ETag, "\"test-etag\"")
					header(HttpHeaders.Expires, GMTDate(now() + 9000L).toHttpDateString())
				}
			)
		}.testUbiquache { client ->
			val stateFlow = ktorStateFlow(
				resultTransform = { response ->
					ResponseResult(
						body = response.body(),
						eTag = response.headers[HttpHeaders.ETag],
					)
				},
			) { cacheControl ->
				client.get("http://test/") {
					cacheControl(cacheControl)
				}
			}

			stateFlow.test {
				assertIs<RequestState.Loading>(awaitItem())

				val result = awaitItem()
				assertIs<RequestState.Result<ResponseResult>>(result)
				assertEquals("body", result.data.body)
				assertEquals("\"test-etag\"", result.data.eTag)
			}
		}
	}

	@Test
	fun transformedCachedResponse() = runTest {
		withServer { number, request ->
			respond(
				content = "body",
				headers = headers {
					header(HttpHeaders.ETag, "\"test-etag\"")
					header(HttpHeaders.Expires, GMTDate(now() + 9000L).toHttpDateString())
				}
			)
		}.testUbiquache { client ->
			suspend fun loadResponse(): ResponseResult {
				var responseResult: ResponseResult? = null
				val stateFlow = ktorStateFlow(
					resultTransform = { response ->
						ResponseResult(
							body = response.body(),
							eTag = response.headers[HttpHeaders.ETag],
						)
					},
				) { cacheControl ->
					client.get("http://test/") {
						cacheControl(cacheControl)
					}
				}

				stateFlow.test {
					assertIs<RequestState.Loading>(awaitItem())

					val result = awaitItem()
					assertIs<RequestState.Result<ResponseResult>>(result)
					responseResult = result.data
				}
				return requireNotNull(responseResult)
			}

			loadResponse()
			val cachedResponse = loadResponse()
			assertEquals(1, responseHistory.size)
			assertEquals(ResponseResult("body", "\"test-etag\""), cachedResponse)
		}
	}

	@Test
	fun transformedCachedResponseDetectsCacheHit() = runTest {
		withServer { number, request ->
			respond(
				content = "body",
				headers = headers {
					header(HttpHeaders.Expires, GMTDate(now() + 9000L).toHttpDateString())
				}
			)
		}.testUbiquache { client ->
			suspend fun loadResponse(): CacheHitResponseResult {
				var responseResult: CacheHitResponseResult? = null
				val stateFlow = ktorStateFlow(
					resultTransform = { response ->
						CacheHitResponseResult(
							body = response.body(),
							isCacheHit = response.isCacheHit(),
						)
					},
				) { cacheControl ->
					client.get("http://test/") {
						cacheControl(cacheControl)
					}
				}

				stateFlow.test {
					assertIs<RequestState.Loading>(awaitItem())

					val result = awaitItem()
					assertIs<RequestState.Result<CacheHitResponseResult>>(result)
					responseResult = result.data
				}
				return requireNotNull(responseResult)
			}

			assertEquals(CacheHitResponseResult("body", false), loadResponse())
			assertEquals(CacheHitResponseResult("body", true), loadResponse())
			assertEquals(1, responseHistory.size)
		}
	}
}
