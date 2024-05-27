package ch.ubique.libs.ktor

import io.ktor.http.HttpHeaders

val HttpHeaders.XBestBefore get() = "X-Best-Before"
val HttpHeaders.XAmzMetaBestBefore get() = "X-Amz-Meta-Best-Before"
val HttpHeaders.XMsMetaBestbefore get() = "X-MS-Meta-Bestbefore"
val HttpHeaders.XNextRefresh get() = "X-Next-Refresh"
val HttpHeaders.XAmzMetaNextRefresh get() = "X-Amz-Meta-Next-Refresh"
val HttpHeaders.XMsMetaNextrefresh get() = "X-MS-Meta-Nextrefresh"
val HttpHeaders.XAmzMetaBackoff get() = "X-Amz-Meta-Backoff"

class CacheControl {
	companion object {
		const val NO_CACHE = "no-cache"
		const val NO_STORE = "no-store"
		const val ONLY_IF_CACHED = "only-if-cached"
	}
}
