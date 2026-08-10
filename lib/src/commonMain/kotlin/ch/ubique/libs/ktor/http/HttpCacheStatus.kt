package ch.ubique.libs.ktor.http

import io.ktor.client.statement.HttpResponse
import io.ktor.http.HeaderValue
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.http.parseHeaderValue
import kotlin.collections.flatMap
import kotlin.collections.orEmpty

val HttpHeaders.CacheStatus get() = "Cache-Status"

const val UbiquacheCacheStatusName = "ubiquache"

internal object CacheStatusForwardReason {
	const val MISS = "miss"
	const val REQUEST = "request"
	const val STALE = "stale"
}

internal fun cacheStatusHitHeader(): Headers {
	return headersOf(HttpHeaders.CacheStatus, "\"$UbiquacheCacheStatusName\"; hit")
}

internal fun cacheStatusForwardHeader(
	forwardReason: String? = null,
	forwardStatus: Int? = null,
	stored: Boolean = false,
): Headers {
	require(forwardStatus == null || forwardReason != null) { "fwd-status requires fwd" }
	require(!stored || forwardReason != null) { "stored requires fwd" }
	val parameters = buildList {
		forwardReason?.let { add("fwd=$it") }
		forwardStatus?.let { add("fwd-status=$it") }
		if (stored) add("stored")
	}
	return headersOf(HttpHeaders.CacheStatus, "\"$UbiquacheCacheStatusName\"; ${parameters.joinToString("; ")}")
}

internal fun HttpResponse.cacheStatusValues(): List<HeaderValue> {
	return headers.getAll(HttpHeaders.CacheStatus)
		.orEmpty()
		.flatMap { parseHeaderValue(it) }
}

internal fun HeaderValue.matchesCacheName(cacheName: String): Boolean {
	return value == cacheName || value == "\"$cacheName\""
}
