package ch.ubique.libs.ktor.cache

import ch.ubique.libs.ktor.StatusLine
import ch.ubique.libs.ktor.cache.db.NetworkCacheDatabase
import ch.ubique.libs.ktor.now
import ch.ubique.libs.ktor.okio.delete
import ch.ubique.libs.ktor.okio.deleteRecursively
import ch.ubique.libs.ktor.okio.ensureDirectory
import ch.ubique.libs.ktor.okio.freeSpace
import io.ktor.client.HttpClient
import io.ktor.client.call.HttpClientCall
import io.ktor.client.call.SavedHttpCall
import io.ktor.client.request.HttpRequest
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.HttpRequestData
import io.ktor.client.statement.HttpResponse
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import io.ktor.util.Attributes
import io.ktor.util.InternalAPI
import io.ktor.util.date.GMTDate
import io.ktor.util.flattenEntries
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.core.toByteArray
import kotlinx.coroutines.Job
import okio.ByteString.Companion.toByteString
import okio.Path
import okio.Source
import okio.buffer
import kotlin.coroutines.CoroutineContext

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
	internal fun storeCacheableResponse(handle: CacheHandle, response: HttpResponse): HttpResponse {
		val cacheMetadata = readCacheMetadata(response)

		if (now() < cacheMetadata.expires || cacheMetadata.etag != null || cacheMetadata.lastModified != null) {
			val head = StatusLine.get(response).toString() + "\r\n" +
					response.headers.asList().joinToString(separator = "") { it.toLine() }

			val bodyStream = response.body?.takeIf { response.promisesBody() }?.byteStream() ?: EmptyInputStream

			cacheDirectory.ensureDirectory()
			handle.storeCachedData(head, bodyStream, cacheMetadata)

			return readCachedResponse(handle, scope, response.request)
		} else {
			return response
		}
	}

	internal fun updateCacheHeaders(handle: CacheHandle, response: HttpResponse) {
		handle.updateCacheMetadata(readCacheMetadata(response))
	}

	private fun readCacheMetadata(response: HttpResponse): CacheEntryMetadata {
		val backoffDate = response.getBackoff()?.plus(now())
		val expires = (response.getExpiresDate() ?: 0L).coerceAtLeast(backoffDate)
		val nextRefresh = (response.getNextRefreshDate() ?: expires).coerceAtLeast(backoffDate)
		val etag = response.header(ETAG)
		val lastModified = response.header(LAST_MODIFIED)?.let { it.toHttpDate()?.time }
		return CacheEntryMetadata(now(), nextRefresh, expires, etag, lastModified)
	}

	@Throws(CachedResourceNotFoundException::class)
	fun getCachedResponseOrThrow(handle: CacheHandle, client: HttpClient, request: HttpRequestBuilder): HttpResponse {
		if (!handle.hasValidCachedResource()) {
			throw CachedResourceNotFoundException(request.url.toString())
		}
		return getCachedResponse(handle, client, request)
	}

	fun getCachedResponse(handle: CacheHandle, client: HttpClient, request: HttpRequestBuilder): HttpResponse {
		handle.updateLastAccessed()
		return readCachedResponse(handle, client, request)
	}

	private fun readCachedResponse(handle: CacheHandle, client: HttpClient, request: HttpRequestBuilder): HttpResponse {
		val (head, body, contentLength) = handle.getCachedData()

		// status line
		val statusLine = StatusLine.parse(head.first())

		// headers
		val headers = Headers.build {
			for (headerLine in head.drop(1)) {
				val (name, value) = headerLine.split(':', limit = 2)
				append(name.trim(), value.trim())
			}
		}

		val nowDate = GMTDate(now())
		val response = object : HttpResponse() {
			override val call: HttpClientCall get() = error("This is a fake response")
			override val status: HttpStatusCode = statusLine.code
			override val version: HttpProtocolVersion = statusLine.protocol
			override val requestTime: GMTDate = nowDate
			override val responseTime: GMTDate = nowDate
			@InternalAPI
			override val content: ByteReadChannel get() = error("This is a fake response")
			override val headers: Headers = headers
			override val coroutineContext: CoroutineContext = request.executionContext
		}
		return CachedHttpCall(client, RequestForCache(request.build()), response, body).response
	}

	private fun computeCacheTag(request: HttpRequestBuilder): String {
		val rawRequest = buildString(512) {
			append(request.method)
			append(' ')
			append(request.url.toString())
			append('\n')

			request.headers.build().flattenEntries()
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
		val cacheTag = rawRequest.toByteArray().toByteString().sha256().base64Url()
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

	fun obtainCacheHandle(request: HttpRequestBuilder): CacheHandle {
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
		return cacheDirectory / "$cacheTag.head"
	}

	private fun bodyCacheFile(cacheTag: String): Path {
		return cacheDirectory / "$cacheTag.body"
	}

	companion object {
		internal const val CACHE_SIZE_AUTO = -1L
		internal const val CACHE_SIZE_AUTO_LIMIT = 128 * 1024 * 1024L

		internal const val CACHE_CLEANUP_INTERVAL = 30 * 1000L

		/** Do not cleanup an expired cache file if it was accessed no longer than this timespan ago (in milliseconds). */
		internal const val LAST_ACCESS_IMMUNITY_TIMESPAN = 10 * 1000L
	}

}


// TODO: move somewhere else

private class RequestForCache(data: HttpRequestData) : HttpRequest {
	override val call: HttpClientCall
		get() = throw IllegalStateException("This request has no call")
	override val method: HttpMethod = data.method
	override val url: Url = data.url
	override val attributes: Attributes = data.attributes
	override val content: OutgoingContent = data.body
	override val headers: Headers = data.headers
}

private class CachedHttpCall(
	client: HttpClient,
	request: HttpRequest,
	response: HttpResponse,
	responseBody: Source
) : HttpClientCall(client) {
	init {
		this.request = CachedHttpRequest(this, request)
		this.response = CachedHttpResponse(this, responseBody, response)
	}
}

internal class CachedHttpRequest(
	override val call: HttpClientCall,
	origin: HttpRequest
) : HttpRequest by origin

internal class CachedHttpResponse(
	override val call: HttpClientCall,
	body: Source,
	origin: HttpResponse
) : HttpResponse() {
	private val context = Job()

	override val status: HttpStatusCode = origin.status

	override val version: HttpProtocolVersion = origin.version

	override val requestTime: GMTDate = origin.requestTime

	override val responseTime: GMTDate = origin.responseTime

	override val headers: Headers = origin.headers

	override val coroutineContext: CoroutineContext = origin.coroutineContext + context

	@InternalAPI
	override val content: ByteReadChannel = ByteReadChannel(body.buffer().readByteArray()) // FIXME: stream instead of copying data

}
