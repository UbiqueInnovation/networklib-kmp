package ch.ubique.libs.ktor.plugins.ubiquache

import ch.ubique.libs.ktor.common.now
import ch.ubique.libs.ktor.getMockStringBlocking
import ch.ubique.libs.ktor.header
import ch.ubique.libs.ktor.http.toHttpDateString
import ch.ubique.libs.ktor.plugins.Ubiquache
import ch.ubique.libs.ktor.testUbiquache
import ch.ubique.libs.ktor.withServer
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.plugin
import io.ktor.http.HttpHeaders
import io.ktor.http.headers
import io.ktor.util.date.GMTDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CacheSizeTest {

	@Test
	fun usedCacheSize() {
		withServer { number, request ->
			respond(
				content = "body",
				headers = headers {
					header(HttpHeaders.Expires, GMTDate(now() + 9000L).toHttpDateString())
				}
			)
		}.testUbiquache { client ->
			val usedBefore = client.plugin(Ubiquache).usedCacheSize()
			assertEquals(0, usedBefore)

			val result = client.getMockStringBlocking()
			assertEquals("body", result)

			val usedAfter = client.plugin(Ubiquache).usedCacheSize()
			assertTrue(usedAfter >= 4)
			assertTrue(usedAfter <= 10_000)
		}
	}

	@Test
	fun maxCacheSize() {
		withServer { number, request ->
			respond(
				content = "body",
				headers = headers {
					header(HttpHeaders.Expires, GMTDate(now() + 9000L).toHttpDateString())
				}
			)
		}.testUbiquache { client ->
			val maxCacheSize = client.plugin(Ubiquache).maxCacheSize()
			assertTrue(maxCacheSize > 0)
		}
	}

}
