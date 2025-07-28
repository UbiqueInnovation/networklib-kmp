package ch.ubique.libs.ktor.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Throws an error since runBlocking is not supported on Kotlin/JS.
 */
actual fun <T> runBlockingOrThrowIfNotSupported(block: suspend () -> T): T {
    throw UnsupportedOperationException("runBlocking is not supported on Kotlin/JS")
}

actual val ioDispatcher: CoroutineDispatcher
    get() = Dispatchers.Default

internal actual val synchrotronDispatcher: CoroutineDispatcher
    get() = Dispatchers.Default