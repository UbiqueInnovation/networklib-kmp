package ch.ubique.libs.ktor.plugins.ubiquache

import ch.ubique.libs.ktor.*
import ch.ubique.libs.ktor.common.exists
import ch.ubique.libs.ktor.common.now
import ch.ubique.libs.ktor.common.writeText
import ch.ubique.libs.ktor.http.toHttpDateString
import ch.ubique.libs.ktor.plugins.Ubiquache
import ch.ubique.libs.ktor.plugins.UbiquacheConfig
import ch.ubique.libs.ktor.plugins.databaseFileName
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.plugin
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headers
import io.ktor.util.date.GMTDate
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class CacheCorruptionTest {

	@Test
	fun cacheDirDeleted() = runTest {
		withServer { number, request ->
			respond(
				content = "#$number",
				headers = headers {
					header(HttpHeaders.Expires, GMTDate(now() + 9000L).toHttpDateString())
				}
			)
		}.testUbiquache { client ->
			run {
				val response = client.getMockResponseBlocking()
				assertEquals("#1", response.bodyAsTextBlocking())
				assertEquals(1, requestHistory.size)
			}
			val cacheDir = UbiquacheConfig.getCacheDir(client.plugin(Ubiquache).name)
			cacheDir.reallyDelete()
			assertFalse(cacheDir.exists())
			run {
				val response = client.getMockResponseBlocking()
				assertEquals("#2", response.bodyAsTextBlocking())
				assertEquals(2, requestHistory.size)
			}
		}
	}

	@Test
	fun databaseFileDeleted() = runTest {
		withServer { number, request ->
			respond(
				content = "#$number",
				headers = headers {
					header(HttpHeaders.Expires, GMTDate(now() + 9000L).toHttpDateString())
				}
			)
		}.testUbiquache { client ->
			run {
				val response = client.getMockResponseBlocking()
				assertEquals("#1", response.bodyAsTextBlocking())
				assertEquals(1, requestHistory.size)
			}
			val dbFile = Path(UbiquacheConfig.getCacheDir(client.plugin(Ubiquache).name), UbiquacheConfig.databaseFileName)
			assertTrue(dbFile.exists())
			dbFile.reallyDelete()
			assertFalse(dbFile.exists())
			run {
				val response = client.getMockResponseBlocking()
				assertEquals("#2", response.bodyAsTextBlocking())
				assertEquals(2, requestHistory.size)
			}
		}
	}

	@Test
	fun etag_cacheFilesDeletedBeforeConditionalRequest() = runTest {
		withServer { number, request ->
			when (number) {
				1 -> respond(
					content = "initial-content",
					headers = headers {
						header(HttpHeaders.ETag, "\"etag-v1\"")
						header(HttpHeaders.Expires, GMTDate(now() + 9000L).toHttpDateString())
					}
				)
				2 -> {
					// With the bug: If-None-Match is sent despite files being gone → server
					// responds 304 → InvalidCacheStateException (nothing to serve from cache).
					// With the fix: conditional headers must not be sent when cache files are
					// absent, so a fresh full response is fetched instead.
					assertNull(request.headers[HttpHeaders.IfNoneMatch])
					assertNull(request.headers[HttpHeaders.IfModifiedSince])
					respond(
						content = "fresh-content",
						headers = headers {
							header(HttpHeaders.ETag, "\"etag-v2\"")
							header(HttpHeaders.Expires, GMTDate(now() + 9000L).toHttpDateString())
						}
					)
				}
				else -> fail("Unexpected request number: $number")
			}
		}.testUbiquache { client ->
			// First request: cache the response with ETag
			assertEquals("initial-content", client.getMockResponseBlocking().bodyAsTextBlocking())
			assertEquals(1, requestHistory.size)

			// Simulate OS evicting cache files while the DB record (including the ETag) survives
			val cacheDir = UbiquacheConfig.getCacheDir(client.plugin(Ubiquache).name)
			SystemFileSystem.list(cacheDir)
				.filter { it.name.endsWith(".head") || it.name.endsWith(".body") }
				.forEach { it.reallyDelete() }

			// Second request: DB still has the ETag but cache files are gone.
			// Should NOT send If-None-Match — there is nothing to serve if the server says 304.
			// Should perform a plain GET and return the fresh response.
			assertEquals("fresh-content", client.getMockResponseBlocking().bodyAsTextBlocking())
			assertEquals(2, requestHistory.size)
		}
	}

	@Test
	fun databaseFileCorrupted() {
		withServer { number, request ->
			respond(
				content = "nfhtcvfd",
				status = HttpStatusCode.OK,
				headers = headers {
					header(HttpHeaders.Expires, GMTDate(now() + 9000L).toHttpDateString())
				}
			)
		}.testUbiquache { client ->
			val dbFile = Path(UbiquacheConfig.getCacheDir(client.plugin(Ubiquache).name), UbiquacheConfig.databaseFileName)
			assertTrue(dbFile.exists())
			dbFile.writeText("SQLite format 3\u0000 kaput!")
			val response = client.getMockResponseBlocking()
			assertEquals("nfhtcvfd", response.bodyAsTextBlocking())
			assertEquals(1, requestHistory.size)
		}
	}

}
