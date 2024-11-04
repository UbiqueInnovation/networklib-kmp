package ch.ubique.libs.ktor.common

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.io.files.Path
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSystemFreeSize

@OptIn(ExperimentalForeignApi::class)
internal actual fun Path.freeSpace(): Long {
	val fileManager = NSFileManager.defaultManager
	val attributes = fileManager.attributesOfFileSystemForPath(this.toString(), null)!!
	return attributes[NSFileSystemFreeSize] as Long
}
