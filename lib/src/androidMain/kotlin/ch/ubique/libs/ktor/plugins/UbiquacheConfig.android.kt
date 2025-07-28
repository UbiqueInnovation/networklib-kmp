package ch.ubique.libs.ktor.plugins

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import ch.ubique.libs.ktor.cache.db.NetworkCacheDatabase
import ch.ubique.libs.ktor.common.ensureDirectory
import kotlinx.io.files.Path

actual object UbiquacheConfig {

	private var applicationContext: Context? = null

	fun init(applicationContext: Context) {
		this.applicationContext = applicationContext.applicationContext
	}

	private fun requireContext(): Context {
		return applicationContext ?: error("UbiquacheConfig not initialized")
	}

	internal actual fun getCacheDir(cacheName: String): Path? {
		return Path(requireContext().cacheDir.path, cacheName)
	}

	internal actual fun createDriver(cacheDir: Path?): SqlDriver {
		if (cacheDir == null) throw IllegalArgumentException("cacheDir cannot be null")
		cacheDir.ensureDirectory()
		return AndroidSqliteDriver(
			schema = NetworkCacheDatabase.Schema,
			context = requireContext(),
			name = Path(cacheDir, databaseFileName).toString(),
		)
	}

}