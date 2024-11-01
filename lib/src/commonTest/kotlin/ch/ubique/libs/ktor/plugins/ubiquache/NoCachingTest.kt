package ch.ubique.libs.ktor.plugins.ubiquache

import ch.ubique.libs.ktor.*
import io.ktor.client.engine.mock.respond
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class NoCachingTest {

	@Test
	fun simpleRequest() {
		withServer { number, request ->
			respond(
				content = "body",
			)
		}.testUbiquache { client ->
			val result = client.getMockStringBlocking()
			assertEquals("body", result)
		}
	}

	@Test
	fun noCaching() {
		withServer { number, request ->
			when (number) {
				1 -> respond(
					content = "mypqlfzr",
				)
				2 -> respond(
					content = "teoxnymd",
				)
				else -> fail("Unexpected request number: $number")
			}
		}.testUbiquache { client ->
			run {
				val response = client.getMockResponseBlocking()
				assertEquals("mypqlfzr", response.bodyAsTextBlocking())
				assertEquals(1, requestHistory.size)
			}
			run {
				val response = client.getMockResponseBlocking()
				assertEquals("teoxnymd", response.bodyAsTextBlocking())
				assertEquals(2, requestHistory.size)
			}
		}
	}

}
