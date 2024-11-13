package ch.ubique.libs.ktor.common

import kotlinx.datetime.Clock

/** Used for testing only. */
private var offset: Long = 0

@Suppress("NOTHING_TO_INLINE")
internal inline fun now() = Clock.System.now().toEpochMilliseconds() + offset

/** Used for testing only. */
internal fun skipTime(millis: Long) {
	offset += millis
}
