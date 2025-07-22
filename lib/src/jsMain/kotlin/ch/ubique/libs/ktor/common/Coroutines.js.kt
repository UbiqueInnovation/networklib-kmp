package ch.ubique.libs.ktor.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Kotlin/JS not implemented
 */
internal actual fun <T> runBlockingOrThrowIfNotSupported(block: suspend () -> T): T {
    throw UnsupportedOperationException("runBlocking is not supported on Kotlin/JS")
}

internal actual val ioDispatcher: CoroutineDispatcher
    get() = Dispatchers.Default

internal actual val synchrotronDispatcher: CoroutineDispatcher
    get() = Dispatchers.Default