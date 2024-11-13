/*
 * Copyright (C) 2013 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ch.ubique.libs.ktor.http

import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode

/** An HTTP response status line like "HTTP/1.1 200 OK". */
class StatusLine(
	val protocol: HttpProtocolVersion,
	val code: HttpStatusCode,
) {

	override fun toString(): String {
		return buildString {
			append(protocol.toString())
			append(' ').append(code.value)
			append(' ').append(code.description)
		}
	}

	companion object {
		fun get(response: HttpResponse): StatusLine {
			return StatusLine(
				response.version,
				response.status,
			)
		}

		fun parse(statusLine: String): StatusLine {
			val (protocolValue, statusCode, statusMessage) = statusLine.split(" ", limit = 3)
			return StatusLine(
				HttpProtocolVersion.parse(protocolValue),
				HttpStatusCode(statusCode.toInt(), statusMessage.trim()),
			)
		}
	}
}
