package ch.ubique.libs.ktor.common

internal inline fun Long.coerceAtLeast(minimumValue: Long?): Long {
	return if (minimumValue != null && this < minimumValue) minimumValue else this
}
