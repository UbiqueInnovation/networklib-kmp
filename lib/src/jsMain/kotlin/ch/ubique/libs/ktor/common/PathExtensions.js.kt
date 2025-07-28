package ch.ubique.libs.ktor.common

import kotlinx.io.files.Path

/**
 * Kotlin/JS not implemented
 */
internal actual fun Path.freeSpace(): Long {
	return 0.toLong()
}
