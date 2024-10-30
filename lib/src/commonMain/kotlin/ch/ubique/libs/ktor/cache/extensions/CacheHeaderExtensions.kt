package ch.ubique.libs.ktor.cache.extensions

import ch.ubique.libs.ktor.common.now
import ch.ubique.libs.ktor.http.*
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HeaderValue
import io.ktor.http.HttpHeaders
import io.ktor.http.cacheControl

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
		?: cacheControl().maxAge()?.takeIf { it > 0 }?.let { now() + it * 1000L }
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
	return headers[HttpHeaders.XAmzMetaBackoff]?.toLongOrNull()?.let { it * 1000L }
}

/**
 * Get the Cache-Control max-age value.
 * @return max-age in millis.
 */
fun List<HeaderValue>.maxAge(): Long? {
	val maxAgeKey = "max-age"
	return firstOrNull { it.value.startsWith(maxAgeKey) }
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
