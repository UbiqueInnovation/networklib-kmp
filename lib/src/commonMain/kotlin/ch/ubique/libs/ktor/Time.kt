package ch.ubique.libs.ktor

import kotlinx.datetime.Clock

/** Used for testing only. */
private var offset: Long = 0

internal inline fun now() = Clock.System.now().toEpochMilliseconds() + offset

/** Used for testing only. */
internal fun skipTime(millis: Long) {
	offset += millis
}
