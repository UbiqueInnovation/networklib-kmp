package ch.ubique.libs.ktor.cache

import ch.ubique.libs.ktor.cache.db.NetworkCacheDatabase
import ch.ubique.libs.ktor.common.*
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readBuffer
import kotlinx.io.Source
import kotlinx.io.files.Path

internal class CacheHandle(
	private val cacheTag: String,
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

	fun hasValidCachedResource(): Boolean {
		return !isExpired() && cachedFilesExist()
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
			body.readBuffer().transferTo(bodyCacheFile.sink())
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

	fun updateCacheMetadata(cacheMetadata: CacheMetadata) {
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
internal data class CacheMetadata(
	var lastAccess: Long = -1,
	var nextRefresh: Long? = null,
	var expires: Long = -1,
	var etag: String? = null,
	var lastModified: Long? = null,
	var size: Long = -1,
)

internal data class CacheData(
	val head: List<String>,
	val body: Source,
)
