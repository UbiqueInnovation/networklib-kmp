package ch.ubique.libs.ktor.common

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.core.writeText
import io.ktor.utils.io.readBuffer
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readLine

private val fs = SystemFileSystem

internal fun Path.exists() = fs.exists(this)

internal fun Path.size() = fs.metadataOrNull(this)?.size ?: 0L

internal fun Path.source() = fs.source(this).buffered()

internal fun Path.readLines() = source().use {
	buildList {
		while (true) {
			add(it.readLine() ?: break)
		}
	}
}

internal fun Path.sink() = fs.sink(this).buffered()

internal fun Path.writeText(text: String) = sink().use {
	it.writeText(text)
}

internal suspend fun Path.writeByteReadChannel(channel: ByteReadChannel) = sink().use {
	channel.readBuffer().transferTo(it)
}

internal fun Path.delete() = runCatching { fs.delete(this, mustExist = false) }

internal fun Path.deleteRecursively() {
	if (fs.metadataOrNull(this)?.isDirectory == true) {
		fs.list(this).forEach { it.deleteRecursively() }
	}
	delete()
}

internal expect fun Path.freeSpace(): Long

internal fun Path.ensureDirectory() {
	if (fs.metadataOrNull(this)?.isDirectory != true) {
		fs.delete(this, mustExist = false)
		fs.createDirectories(this)
	}
}

// for whatever reason, the use function from the stdlib is not usable
@OptIn(ExperimentalStdlibApi::class)
internal inline fun <T : AutoCloseable, R> T.use(block: (T) -> R): R {
	try {
		return block(this)
	} finally {
		close()
	}
}