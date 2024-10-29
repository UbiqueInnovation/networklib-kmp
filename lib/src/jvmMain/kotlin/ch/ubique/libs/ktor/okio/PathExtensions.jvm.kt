package ch.ubique.libs.ktor.okio

import okio.Path

internal actual fun Path.freeSpace(): Long {
	return this.toFile().usableSpace
}
