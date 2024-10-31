package ch.ubique.libs.ktor.plugins.ubiquache

import ch.ubique.libs.ktor.*
import ch.ubique.libs.ktor.common.deleteRecursively
import ch.ubique.libs.ktor.common.exists
import ch.ubique.libs.ktor.common.now
import ch.ubique.libs.ktor.common.writeText
import ch.ubique.libs.ktor.http.toHttpDateString
import ch.ubique.libs.ktor.plugins.Ubiquache
import ch.ubique.libs.ktor.plugins.UbiquacheConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.plugin
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headers
import io.ktor.util.date.GMTDate
import kotlinx.io.files.Path
import kotlin.test.*

class CacheErrorTest {

	@Test
	@Ignore // TODO: implement database recovery
	fun cacheDirDeleted()  {
		withServer { number, request ->
			when (number) {
				1 -> respond(
					content = "pduednzf",
					status = HttpStatusCode.OK,
					headers = headers {
						header(HttpHeaders.Expires, GMTDate(now() + 10000L).toHttpDateString())
						header(HttpHeaders.ContentType, "text/plain")
					}
				)
				2 -> respond(
					content = "ndurmfqk",
					status = HttpStatusCode.OK,
					headers = headers {
						header(HttpHeaders.Expires, GMTDate(now() + 10000L).toHttpDateString())
						header(HttpHeaders.ContentType, "text/plain")
					}
				)
				else -> fail("Unexpected request number: $number")
			}
		}.testUbiquache { client ->
			run {
				val response = client.getMockResponseBlocking()
				assertEquals("pduednzf", response.bodyAsTextBlocking())
				assertEquals("text/plain", response.headers[HttpHeaders.ContentType])
				assertEquals(1, requestHistory.size)
			}
			UbiquacheConfig.getCacheDir(client.plugin(Ubiquache).name).deleteRecursively()
			run {
				val response = client.getMockResponseBlocking()
				assertEquals("ndurmfqk", response.bodyAsTextBlocking())
				assertEquals("text/plain", response.headers[HttpHeaders.ContentType])
				assertEquals(2, requestHistory.size)
			}
		}
	}

	@Test
	@Ignore // TODO: implement database recovery
	fun cacheDatabaseCorrupted() {
		withServer { number, request ->
			respond(
				content = "nfhtcvfd",
				status = HttpStatusCode.OK,
				headers = headers {
					header(HttpHeaders.Expires, GMTDate(now() + 10000L).toHttpDateString())
					header(HttpHeaders.ContentType, "text/plain")
				}
			)
		}.testUbiquache { client ->
			val dbFile = Path(UbiquacheConfig.getCacheDir(client.plugin(Ubiquache).name), "cache.db")
			assertTrue(dbFile.exists())
			dbFile.writeText("SQLite format 3\u0000 kaputt!")
				val response = client.getMockResponseBlocking()
				assertEquals("nfhtcvfd", response.bodyAsTextBlocking())
				assertEquals("text/plain", response.headers[HttpHeaders.ContentType])
				assertEquals(1, requestHistory.size)
		}
	}

}
