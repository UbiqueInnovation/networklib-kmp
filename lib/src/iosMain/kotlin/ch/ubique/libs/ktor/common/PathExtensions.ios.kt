package ch.ubique.libs.ktor.common

import kotlinx.io.files.Path
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSystemFreeSize

internal actual fun Path.freeSpace(): Long {
	val fileManager = NSFileManager.defaultManager
	val attributes = fileManager.attributesOfFileSystemForPath(this.toString(), null)
	return attributes.objectForKey(NSFileSystemFreeSize) as Long
}
