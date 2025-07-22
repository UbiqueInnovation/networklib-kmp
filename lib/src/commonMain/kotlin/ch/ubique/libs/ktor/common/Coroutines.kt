package ch.ubique.libs.ktor.common

import kotlinx.coroutines.CoroutineDispatcher

expect fun <T> runBlockingOrThrowIfNotSupported(block: suspend () -> T): T

expect val ioDispatcher: CoroutineDispatcher

internal expect val synchrotronDispatcher: CoroutineDispatcher
