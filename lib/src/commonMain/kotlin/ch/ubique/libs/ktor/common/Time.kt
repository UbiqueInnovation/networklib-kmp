package ch.ubique.libs.ktor.common

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Used for testing only. */
private var offset: Long = 0

@OptIn(ExperimentalTime::class)
@Suppress("NOTHING_TO_INLINE")
internal inline fun now() = Clock.System.now().toEpochMilliseconds() + offset

/** Used for testing only. */
internal fun skipTime(millis: Long) {
	offset += millis
}
