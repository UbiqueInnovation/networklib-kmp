package ch.ubique.libs.ktor.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.newSingleThreadContext

internal actual fun <T> runBlockingOrThrowIfNotSupported(block: suspend () -> T): T {
    return runBlocking { block() }
}

actual val ioDispatcher: CoroutineDispatcher
    get() = Dispatchers.IO

@OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
internal actual val synchrotronDispatcher: CoroutineDispatcher by lazy {
    newSingleThreadContext("Synchrotron")
}
