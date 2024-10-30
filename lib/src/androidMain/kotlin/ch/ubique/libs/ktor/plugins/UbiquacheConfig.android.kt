package ch.ubique.libs.ktor.plugins

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import ch.ubique.libs.ktor.cache.db.NetworkCacheDatabase
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

actual object UbiquacheConfig {

	private var applicationContext: Context? = null

	fun init(applicationContext: Context) {
		this.applicationContext = applicationContext.applicationContext
	}

	private fun requireContext(): Context {
		return applicationContext ?: error("UbiquacheConfig not initialized")
	}

	internal actual fun getCacheDir(cacheName: String): Path {
		return Path(requireContext().cacheDir.path, cacheName)
	}

	internal actual fun createDriver(cacheDir: Path): SqlDriver {
		SystemFileSystem.createDirectories(cacheDir)
		return AndroidSqliteDriver(
			schema = NetworkCacheDatabase.Schema,
			context = requireContext(),
			name = Path(cacheDir, "cache.db").toString(),
		)
	}

}