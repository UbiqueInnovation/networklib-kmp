package ch.ubique.libs.ktor.cache.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import java.io.File

actual class CacheDatabaseDriverFactory actual constructor() {

	actual fun createDriver(cacheName: String): SqlDriver {
		val context = requireNotNull(Cache.applicationContext) { "Cache.applicationContext not initialized" }
		val dir = File(context.cacheDir, cacheName)
		dir.mkdirs()
		val dbFile = File(dir, "cache.db")
		return AndroidSqliteDriver(
			schema = NetworkCacheDatabase.Schema,
			context = context,
			name = dbFile.absolutePath,
		)
	}

}
