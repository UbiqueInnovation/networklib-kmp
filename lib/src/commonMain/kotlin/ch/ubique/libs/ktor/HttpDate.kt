package ch.ubique.libs.ktor

import io.ktor.http.fromHttpToGmtDate
import io.ktor.http.toHttpDate
import io.ktor.util.date.GMTDate

/** Returns the date for this string, or null if the value couldn't be parsed. */
internal fun String.toHttpDate(): GMTDate? {
	return runCatching { fromHttpToGmtDate() }.getOrNull()
}

/** Returns the string for this date. */
internal fun GMTDate.toHttpDateString(): String = toHttpDate()
