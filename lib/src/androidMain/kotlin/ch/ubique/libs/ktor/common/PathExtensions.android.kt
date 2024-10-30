package ch.ubique.libs.ktor.common

import kotlinx.io.files.Path
import java.io.File

internal actual fun Path.freeSpace(): Long {
	return File(toString()).usableSpace
}
