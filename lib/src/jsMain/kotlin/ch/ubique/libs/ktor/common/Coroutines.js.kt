package ch.ubique.libs.ktor.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

actual fun <T> runBlockingOrThrowIfNotSupported(block: suspend () -> T): T {
    throw UnsupportedOperationException("runBlocking is not supported on Kotlin/JS")
}

actual val ioDispatcher: CoroutineDispatcher
    get() = Dispatchers.Default

actual val synchrotronDispatcher: CoroutineDispatcher
    get() = Dispatchers.Default