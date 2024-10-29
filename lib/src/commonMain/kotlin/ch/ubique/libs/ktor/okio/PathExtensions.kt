package ch.ubique.libs.ktor.okio

import okio.FileSystem
import okio.Path
import okio.SYSTEM
import okio.use

private val fs = FileSystem.SYSTEM

internal fun Path.exists() = fs.exists(this)

internal fun Path.size() = fs.metadataOrNull(this)?.size ?: 0

internal fun Path.source() = fs.source(this)

internal fun Path.readLines() = fs.read(this) {
	buildList {
		while (true) {
			add(readUtf8Line() ?: break)
		}
	}
}

internal fun Path.writeText(text: String) = fs.write(this) {
	writeUtf8(text)
}

internal fun Path.writeSource(source: okio.Source) = fs.write(this) {
	source.use { input ->
		writeAll(input)
	}
}

internal fun Path.delete() = fs.delete(this)

internal fun Path.deleteRecursively() = fs.deleteRecursively(this)

internal expect fun Path.freeSpace(): Long

internal fun Path.ensureDirectory() {
	if (fs.metadataOrNull(this)?.isDirectory != true) {
		fs.delete(this)
		fs.createDirectories(this)
	}
}
