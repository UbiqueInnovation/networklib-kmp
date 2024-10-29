package ch.ubique.libs.ktor

import io.ktor.client.utils.CacheControl
import io.ktor.http.HeaderValue
import io.ktor.http.HttpHeaders

val HttpHeaders.XBestBefore get() = "X-Best-Before"
val HttpHeaders.XAmzMetaBestBefore get() = "X-Amz-Meta-Best-Before"
val HttpHeaders.XMsMetaBestbefore get() = "X-MS-Meta-Bestbefore"
val HttpHeaders.XNextRefresh get() = "X-Next-Refresh"
val HttpHeaders.XAmzMetaNextRefresh get() = "X-Amz-Meta-Next-Refresh"
val HttpHeaders.XMsMetaNextrefresh get() = "X-MS-Meta-Nextrefresh"
val HttpHeaders.XAmzMetaBackoff get() = "X-Amz-Meta-Backoff"

val HttpHeaders.XUbiquache get() = "X-Ubiquache"

object CacheControlValue {
	val NO_CACHE = HeaderValue(CacheControl.NO_CACHE)
	val NO_STORE = HeaderValue(CacheControl.NO_STORE)
	val ONLY_IF_CACHED = HeaderValue(CacheControl.ONLY_IF_CACHED)
}
