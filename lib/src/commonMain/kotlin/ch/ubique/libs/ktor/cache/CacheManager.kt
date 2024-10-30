package ch.ubique.libs.ktor.cache

import ch.ubique.libs.ktor.cache.db.NetworkCacheDatabase
import ch.ubique.libs.ktor.cache.extensions.backoff
import ch.ubique.libs.ktor.cache.extensions.expiresDate
import ch.ubique.libs.ktor.cache.extensions.nextRefreshDate
import ch.ubique.libs.ktor.common.*
import ch.ubique.libs.ktor.http.StatusLine
import ch.ubique.libs.ktor.http.toHttpDate
import ch.ubique.libs.ktor.http.toHttpRequest
import ch.ubique.libs.ktor.http.toHttpRequestData
import io.ktor.client.HttpClient
import io.ktor.client.call.HttpClientCall
import io.ktor.client.plugins.cache.InvalidCacheStateException
import io.ktor.client.request.HttpRequestData
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.request
import io.ktor.http.*
import io.ktor.util.date.GMTDate
import io.ktor.util.flattenEntries
import io.ktor.util.sha1
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.core.toByteArray
import kotlinx.io.files.Path
import kotlin.coroutines.CoroutineContext
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Cache manager bridging Ktor requests and responses with the cached data (file cache and metadata database).
 */
internal class CacheManager(
	private val cacheDirectory: Path,
	private val cacheMetadataDatabase: NetworkCacheDatabase,
	private val cacheMaxSize: Long = CACHE_SIZE_AUTO,
) {

	val usedSize: Long get() = usedCacheSize()

	val maxSize: Long get() = if (cacheMaxSize == CACHE_SIZE_AUTO) maxCacheSize() else cacheMaxSize

	private var lastCleanupRun: Long = 0L

	/**
	 * Store the response if it qualifies for caching. Returns a response instance that can be used further.
	 */
	internal suspend fun storeCacheableResponse(handle: CacheHandle, response: HttpResponse): HttpResponse? {
		val cacheMetadata = response.getCacheMetadata()

		if (now() < cacheMetadata.expires || cacheMetadata.etag != null || cacheMetadata.lastModified != null) {
			val head = StatusLine.get(response).toString() + "\r\n" +
					response.headers.flattenEntries().joinToString(separator = "\r\n") { it.first + ": " + it.second }

			val bodyStream = response.bodyAsChannel()

			cacheDirectory.ensureDirectory()
			handle.storeCachedData(head, bodyStream, cacheMetadata)

			return readCachedResponse(handle, response.call.client, response.request.toHttpRequestData())
		} else {
			return null
		}
	}

	internal fun updateCacheHeaders(handle: CacheHandle, response: HttpResponse) {
		handle.updateCacheMetadata(response.getCacheMetadata())
	}

	private fun HttpResponse.getCacheMetadata(): CacheMetadata {
		val backoffDate = backoff()?.plus(now())
		val expires = (expiresDate() ?: 0L).coerceAtLeast(backoffDate)
		val nextRefresh = (nextRefreshDate() ?: expires).coerceAtLeast(backoffDate)
		val etag = headers[HttpHeaders.ETag]
		val lastModified = headers[HttpHeaders.LastModified]?.let { it.toHttpDate()?.timestamp }
		return CacheMetadata(now(), nextRefresh, expires, etag, lastModified)
	}

	fun getCachedResponse(handle: CacheHandle, client: HttpClient, request: HttpRequestData): HttpResponse {
		handle.updateLastAccessed()
		return readCachedResponse(handle, client, request) ?: throw InvalidCacheStateException(request.url)
	}

	private fun readCachedResponse(handle: CacheHandle, client: HttpClient, request: HttpRequestData): HttpResponse? {
		val (head, body) = handle.getCachedData() ?: return null

		// status line
		val statusLine = StatusLine.parse(head.first())

		// headers
		val headers = headers {
			for (headerLine in head.drop(1)) {
				val (name, value) = headerLine.split(':', limit = 2)
				append(name.trim(), value.trim())
			}
		}

		val nowDate = GMTDate(now())
		val response = object : HttpResponse() {
			@InternalAPI
			override val rawContent: ByteReadChannel get() = error("This is a fake response")
			override val call: HttpClientCall get() = error("This is a fake response")
			override val status: HttpStatusCode = statusLine.code
			override val version: HttpProtocolVersion = statusLine.protocol
			override val requestTime: GMTDate = nowDate
			override val responseTime: GMTDate = nowDate
			override val headers: Headers = headers
			override val coroutineContext: CoroutineContext = request.executionContext
		}
		return CachedHttpCall(client, request.toHttpRequest(), response, body).response
	}

	private fun computeCacheTag(request: HttpRequestData): String {
		val rawRequest = buildString(512) {
			append(request.method)
			append(' ')
			append(request.url.toString())
			append('\n')

			request.headers.flattenEntries()
				.map { header -> header.first.lowercase() to header.second }
				.filter { header ->
					header.first == "accept" || header.first.startsWith("accept-") || header.first == "authorization"
				}
				.forEach { header ->
					append(header.first)
					append(':')
					append(header.second)
					append('\n')
				}
		}
		@OptIn(ExperimentalEncodingApi::class)
		val cacheTag = Base64.UrlSafe.encode(sha1(rawRequest.toByteArray())).dropLast(1)
		return cacheTag
	}

	/**
	 * Remove the cached file and its data in the cache database.
	 */
	private fun removeCachedResource(cacheTag: String) {
		// TODO: need lock here
		// delete database record
		cacheMetadataDatabase.networkCacheDatabaseQueries.remove(cacheTag)
		// delete files
		headCacheFile(cacheTag).delete()
		bodyCacheFile(cacheTag).delete()
	}

	/**
	 * Clear the cache, deleting all files in the cache. Pending cache operations might fail.
	 */
	fun clearCache() {
		cacheDirectory.deleteRecursively()
	}

	/**
	 * Clear the cache for a specific URL or all URLs with a common prefix.
	 */
	fun clearCache(url: String, isPrefix: Boolean = false) {
		// TODO: delete cache entries by URLs
	}

	private fun maxCacheSize(): Long {
		if (cacheMaxSize == CACHE_SIZE_AUTO) {
			val availableSpace: Long = cacheDirectory.freeSpace() - usedCacheSize()
			return (availableSpace / 2).coerceAtMost(CACHE_SIZE_AUTO_LIMIT)
		} else {
			return cacheMaxSize
		}
	}

	private fun usedCacheSize(): Long {
		return cacheMetadataDatabase.networkCacheDatabaseQueries.getUsedCacheSize().executeAsOne().SUM ?: 0L
	}

	fun obtainCacheHandle(request: HttpRequestData): CacheHandle {
		request.executionContext.invokeOnCompletion {
			// TODO: release lock here?
		}
		// TODO: wait here for lock?
		val cacheTag = computeCacheTag(request)
		return CacheHandle(
			cacheTag,
			request.url.toString(),
			headCacheFile(cacheTag),
			bodyCacheFile(cacheTag),
			cacheMetadataDatabase,
		)
	}

	private fun headCacheFile(cacheTag: String): Path {
		return Path(cacheDirectory, "$cacheTag.head")
	}

	private fun bodyCacheFile(cacheTag: String): Path {
		return Path(cacheDirectory, "$cacheTag.body")
	}

	companion object {
		internal const val CACHE_SIZE_AUTO = -1L
		internal const val CACHE_SIZE_AUTO_LIMIT = 128 * 1024 * 1024L

		internal const val CACHE_CLEANUP_INTERVAL = 30 * 1000L

		/** Do not cleanup an expired cache file if it was accessed no longer than this timespan ago (in milliseconds). */
		internal const val LAST_ACCESS_IMMUNITY_TIMESPAN = 10 * 1000L
	}

}
