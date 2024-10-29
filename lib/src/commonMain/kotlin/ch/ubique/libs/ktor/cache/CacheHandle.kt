package ch.ubique.libs.ktor.cache

import ch.ubique.libs.ktor.cache.db.NetworkCacheDatabase
import ch.ubique.libs.ktor.now
import ch.ubique.libs.ktor.okio.*
import okio.Path
import okio.Source

internal class CacheHandle(
	private val cacheTag: String,
	private val url: String,
	private val headCacheFile: Path,
	private val bodyCacheFile: Path,
	private val cacheMetadataDatabase: NetworkCacheDatabase,
) {

	private val db = cacheMetadataDatabase.networkCacheDatabaseQueries

	private var _liveCacheMetadata: CacheEntryMetadata? = null
	private val liveCacheMetadata: CacheEntryMetadata
		get() = _liveCacheMetadata ?: getCacheMetaData().also { _liveCacheMetadata = it }

	private fun getCacheMetaData(): CacheEntryMetadata {
		return db.get(cacheTag).executeAsOneOrNull()?.run {
			CacheEntryMetadata(lastaccess, refresh, expire, etag, lastmod, size)
		} ?: CacheEntryMetadata()
	}

	fun hasValidCachedResource(): Boolean {
		return !isExpired() && cachedFilesExist()
	}

	fun cachedFilesExist(): Boolean {
		return headCacheFile.exists() && bodyCacheFile.exists()
	}

	fun getCachedData(): CachedData {
		val head = headCacheFile.readLines()
		val body = bodyCacheFile.source()
		val contentLength = bodyCacheFile.size()
		return CachedData(head, body, contentLength)
	}

	fun storeCachedData(head: String, body: Source, cacheMetadata: CacheEntryMetadata) {
		try {
			headCacheFile.writeText(head)
			bodyCacheFile.writeSource(body)
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

	fun updateCacheMetadata(cacheMetadata: CacheEntryMetadata) {
		cacheMetadata.apply {
			db.update(lastAccess, nextRefresh, expires, etag, lastModified, size, cacheTag)
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

	fun isExpired(): Boolean {
		return liveCacheMetadata.expires < now()
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
internal data class CacheEntryMetadata(
	var lastAccess: Long = -1,
	var nextRefresh: Long? = null,
	var expires: Long = -1,
	var etag: String? = null,
	var lastModified: Long? = null,
	var size: Long = -1,
)

internal data class CachedData(
	val head: List<String>,
	val body: Source,
	val contentLength: Long,
)
