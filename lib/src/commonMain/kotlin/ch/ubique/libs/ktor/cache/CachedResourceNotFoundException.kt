package ch.ubique.libs.ktor.cache

import io.ktor.utils.io.errors.IOException

/**
 * Exception indicating that the cached response has been requested but was not stored in cache.
 */
class CachedResourceNotFoundException(
	val url: String
) : IOException("Missing data in cache for $url")
