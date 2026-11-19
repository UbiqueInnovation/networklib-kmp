package ch.ubique.libs.ktor.cache

import ch.ubique.libs.ktor.cache.db.NetworkCacheDatabase
import ch.ubique.libs.ktor.cache.extensions.maxStale
import ch.ubique.libs.ktor.cache.extensions.minFresh
import ch.ubique.libs.ktor.common.delete
import ch.ubique.libs.ktor.common.exists
import ch.ubique.libs.ktor.common.now
import ch.ubique.libs.ktor.common.readLines
import ch.ubique.libs.ktor.common.size
import ch.ubique.libs.ktor.common.source
import ch.ubique.libs.ktor.common.writeByteReadChannel
import ch.ubique.libs.ktor.common.writeText
import ch.ubique.libs.ktor.common.writeTextAtomic
import io.ktor.http.HeaderValue
import io.ktor.http.Headers
import io.ktor.utils.io.ByteReadChannel
import kotlinx.io.Source
import kotlinx.io.files.Path

internal class CacheHandle(
	internal val cacheTag: String,
	private val url: String,
	private val headCacheFile: Path,
	private val bodyCacheFile: Path,
	private val cacheMetadataDatabase: NetworkCacheDatabase,
) {

	private val db = cacheMetadataDatabase.networkCacheDatabaseQueries

	private var _liveCacheMetadata: CacheMetadata? = null
	private val liveCacheMetadata: CacheMetadata
		get() = _liveCacheMetadata ?: getCacheMetaData().also { _liveCacheMetadata = it }

	private fun getCacheMetaData(): CacheMetadata {
		return db.get(cacheTag).executeAsOneOrNull()?.run {
			CacheMetadata(lastaccess, refresh, expire, etag, lastmod, size)
		} ?: CacheMetadata()
	}

	fun hasValidCachedResource(requestCacheControl: List<HeaderValue>? = null, now: Long): Boolean {
		if (!liveCacheMetadata.isValid()) {
			return false
		}

		val ttl = getTTL(now)
		val maxStale = requestCacheControl?.maxStale() ?: 0;
		val minFresh = requestCacheControl?.minFresh()
		val ttlOk = (ttl > 0 || (-ttl < maxStale)) && (minFresh == null || ttl > minFresh)

		return ttlOk && cachedFilesExist()
	}

	fun cachedFilesExist(): Boolean {
		return headCacheFile.exists() && bodyCacheFile.exists()
	}

	fun getCachedData(): CacheData? {
		if (!cachedFilesExist()) return null
		val head = headCacheFile.readLines()
		val body = bodyCacheFile.source()
		return CacheData(head, body)
	}

	suspend fun storeCachedData(head: String, body: ByteReadChannel, cacheMetadata: CacheMetadata) {
		try {
			headCacheFile.writeText(head)
			bodyCacheFile.writeByteReadChannel(body)
			val fileSize = headCacheFile.size() + bodyCacheFile.size()
			liveCacheMetadata.apply {
				lastAccess = now()
				nextRefresh = cacheMetadata.nextRefresh
				expires = cacheMetadata.expires
				etag = cacheMetadata.etag
				lastModified = cacheMetadata.lastModified
				size = fileSize
				db.save(cacheTag, url, lastAccess, nextRefresh, expires, etag, lastModified, size)
			}
		} catch (e: Exception) {
			headCacheFile.delete()
			bodyCacheFile.delete()
			_liveCacheMetadata = null
			throw e
		}
	}

	fun updateCacheMetadata(cacheMetadata: CacheMetadata, updateHead: (oldHead: List<String>) -> String) {
		val head = updateHead(headCacheFile.readLines())
		try {
			headCacheFile.writeTextAtomic(head)
		} catch (e: Exception) {
			return
		}
		cacheMetadata.apply {
			db.update(lastAccess, nextRefresh, expires, etag, lastModified, cacheTag)
		}
	}

	fun updateLastAccessed() {
		val currentTime = now()
		liveCacheMetadata.lastAccess = currentTime
		db.updateAccessed(currentTime, cacheTag)
	}

	fun removeFromCache() {
		headCacheFile.delete()
		bodyCacheFile.delete()
		db.remove(cacheTag)
		_liveCacheMetadata = null
	}

	fun shouldBeRefreshedButIsNotExpired(): Boolean {
		return liveCacheMetadata.nextRefresh?.let { nextRefresh ->
			val now: Long = now()
			return (nextRefresh in 1..now && now < liveCacheMetadata.expires)
		} ?: false
	}

	fun getTTL(now: Long): Long {
		return liveCacheMetadata.expires - now;
	}

	fun isExpired(now: Long): Boolean {
		return getTTL(now) < 0;
	}

	fun getETag(): String? {
		return liveCacheMetadata.etag
	}

	fun getLastModified(): Long {
		return liveCacheMetadata.lastModified ?: -1L
	}
}

/**
 * @param lastAccess unix timestamp (milliseconds)
 * @param nextRefresh (optional) unix timestamp (milliseconds)
 * @param expires unix timestamp (milliseconds)
 * @param etag (optional) HTTP ETag
 * @param lastModified (optional) HTTP Last-Modified (unix timestamp, milliseconds)
 * @param size total file size (headers and body) in bytes
 */
internal data class CacheMetadata(
	var lastAccess: Long = -1,
	var nextRefresh: Long? = null,
	var expires: Long = -1,
	var etag: String? = null,
	var lastModified: Long? = null,
	var size: Long = -1,
) {
	fun isValid(): Boolean {
		return size >= 0
	}
}

internal data class CacheData(
	val head: List<String>,
	val body: Source,
)
