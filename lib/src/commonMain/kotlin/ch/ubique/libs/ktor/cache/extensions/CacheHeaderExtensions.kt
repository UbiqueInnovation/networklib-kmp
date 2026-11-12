package ch.ubique.libs.ktor.cache.extensions

import ch.ubique.libs.ktor.common.now
import ch.ubique.libs.ktor.http.*
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HeaderValue
import io.ktor.http.HttpHeaders
import io.ktor.http.cacheControl
import kotlin.collections.firstOrNull

/**
 * Get the date of this response.
 * @return Unix timestamp in millis.
 */
fun HttpResponse.serverDate(): Long? {
	return headers[HttpHeaders.Date]?.let { it.toHttpDate()?.timestamp }
}

/**
 * Get the expiry date of this response.
 * @return Unix timestamp in millis.
 */
fun HttpResponse.expiresDate(): Long? {
	return headers[HttpHeaders.XBestBefore]?.let { it.toHttpDate()?.timestamp }
		?: headers[HttpHeaders.XAmzMetaBestBefore]?.let { it.toHttpDate()?.timestamp }
		?: headers[HttpHeaders.XMsMetaBestbefore]?.let { it.toHttpDate()?.timestamp }
		?: headers[HttpHeaders.Expires]?.let { it.toHttpDate()?.timestamp }
		?: cacheControl().maxAge()?.takeIf { it > 0 }?.plus(now())
}

/**
 * Get the next refresh date of this response.
 * @return Unix timestamp in millis.
 */
fun HttpResponse.nextRefreshDate(): Long? {
	return headers[HttpHeaders.XNextRefresh]?.let { it.toHttpDate()?.timestamp }
		?: headers[HttpHeaders.XAmzMetaNextRefresh]?.let { it.toHttpDate()?.timestamp }
		?: headers[HttpHeaders.XMsMetaNextrefresh]?.let { it.toHttpDate()?.timestamp }
}

/**
 * Get the backoff in millis of this response.
 * @return backoff in millis.
 */
fun HttpResponse.backoff(): Long? {
	return headers[HttpHeaders.XBackoff]?.toLongOrNull()?.let { it * 1000L }
		?: headers[HttpHeaders.XAmzMetaBackoff]?.toLongOrNull()?.let { it * 1000L }
		?: headers[HttpHeaders.XMsMetaBackoff]?.toLongOrNull()?.let { it * 1000L }
}

/**
 * Get the Cache-Control max-age value.
 * @return max-age in millis.
 */
fun List<HeaderValue>.maxAge(): Long? =
	extractDurationValueAsMillis(this, "max-age")

/**
 * Get the Cache-Control max-stale value.
 * @return max-stale in millis.
 */
fun List<HeaderValue>.maxStale(): Long? =
	extractDurationValueAsMillis(this, "max-stale")

/**
 * Get the Cache-Control min-fresh value.
 * @return min-fresh in millis.
 */
fun List<HeaderValue>.minFresh(): Long? =
	extractDurationValueAsMillis(this, "min-fresh")

private fun extractDurationValueAsMillis(values: List<HeaderValue>, key: String): Long? {
	return values.firstOrNull { it.value.startsWith(key) }
		?.value
		?.split("=")
		?.getOrNull(1)
		?.toLongOrNull()
		?.times(1000L)
}

/**
 * Set a cache control header.
 */
fun HttpRequestBuilder.cacheControl(cacheControl: String?) {
	header(HttpHeaders.CacheControl, cacheControl)
}

/**
 * Returns whether the specified cache served this response directly from its cache.
 */
fun HttpResponse.isCacheHit(cacheName: String = UbiquacheCacheStatusName): Boolean {
	return cacheStatusValues().any { cacheStatus ->
		cacheStatus.matchesCacheName(cacheName) &&
				cacheStatus.params.any { it.name == "hit" && it.value.isEmpty() }
	}
}

/**
 * Returns whether the specified cache supplied the response content from its cache.
 *
 * This includes direct cache hits, responses reconstructed after a successful 304
 * validation, and responses served from cache after a failed refresh.
 */
fun HttpResponse.isReadFromCache(cacheName: String = UbiquacheCacheStatusName): Boolean {
	return cacheStatusValues().any { cacheStatus ->
		if (!cacheStatus.matchesCacheName(cacheName)) {
			return@any false
		}
		val hasHit = cacheStatus.params.any { it.name == "hit" && it.value.isEmpty() }
		val wasValidated = cacheStatus.params.any { it.name == "fwd-status" && it.value == "304" }
		val wasFallback = cacheStatus.params.any { it.name == "fwd" && it.value == "miss" } &&
			cacheStatus.params.none { it.name == "stored" && it.value.isEmpty() }
		hasHit || wasValidated || wasFallback
	}
}
