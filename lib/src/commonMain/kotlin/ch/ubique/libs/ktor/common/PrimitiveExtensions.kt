package ch.ubique.libs.ktor.common

@Suppress("NOTHING_TO_INLINE")
internal inline fun Long.coerceAtLeast(minimumValue: Long?): Long {
	return if (minimumValue != null && this < minimumValue) minimumValue else this
}
