package ch.ubique.libs.ktor.cache

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class CacheAccessSynchronization {

	private val synchronized = Mutex()

	private val locks: MutableMap<String, Mutex> = HashMap()

	suspend inline fun <R> withLock(tag: String, block: () -> R): R {
		val mutex = synchronized.withLock {
			locks.getOrPut(tag) { Mutex() }
		}
		return mutex.withLock {
			block()
		}
	}

}
