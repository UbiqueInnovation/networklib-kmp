package ch.ubique.libs.ktor.cache.db

import kotlinx.io.IOException

class CacheDatabaseException(message: String, cause: Throwable? = null) : IOException(message, cause)
