package ch.ubique.libs.ktor.common

import kotlinx.io.files.Path

internal actual fun Path.freeSpace(): Long {
	return 1e9.toLong()
}
