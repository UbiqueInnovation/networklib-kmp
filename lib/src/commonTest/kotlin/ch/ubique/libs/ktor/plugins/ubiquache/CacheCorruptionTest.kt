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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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
			assertNotNull(cacheDir)
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
			val cacheDir = UbiquacheConfig.getCacheDir(client.plugin(Ubiquache).name)
			assertNotNull(cacheDir)
			val dbFile = Path(cacheDir, UbiquacheConfig.databaseFileName)
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
			val cacheDir = UbiquacheConfig.getCacheDir(client.plugin(Ubiquache).name)
			assertNotNull(cacheDir)
			val dbFile = Path(cacheDir, UbiquacheConfig.databaseFileName)
			assertTrue(dbFile.exists())
			dbFile.writeText("SQLite format 3\u0000 kaput!")
			val response = client.getMockResponseBlocking()
			assertEquals("nfhtcvfd", response.bodyAsTextBlocking())
			assertEquals(1, requestHistory.size)
		}
	}

}
