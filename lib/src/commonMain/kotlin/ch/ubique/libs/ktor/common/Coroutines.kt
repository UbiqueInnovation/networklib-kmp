package ch.ubique.libs.ktor.common

import kotlinx.coroutines.CoroutineDispatcher

internal expect fun <T> runBlockingOrThrowIfNotSupported(block: suspend () -> T): T

internal expect val ioDispatcher: CoroutineDispatcher

internal expect val synchrotronDispatcher: CoroutineDispatcher
