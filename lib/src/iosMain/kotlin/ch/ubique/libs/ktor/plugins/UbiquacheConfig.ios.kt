package ch.ubique.libs.ktor.plugins

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import ch.ubique.libs.ktor.cache.db.NetworkCacheDatabase
import co.touchlab.sqliter.DatabaseConfiguration
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

actual object UbiquacheConfig {

	internal actual fun getCacheDir(cacheName: String): Path {
		val cacheDirectory: String = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true).first().toString()
		return cacheDirectory.toPath() / cacheName
	}

	internal actual fun createDriver(cacheDir: Path): SqlDriver {
		FileSystem.SYSTEM.createDirectories(cacheDir)
		return NativeSqliteDriver(
			schema = NetworkCacheDatabase.Schema,
			name = "cache.db",
			onConfiguration = {
				it.copy(
					extendedConfig = DatabaseConfiguration.Extended(
						basePath = cacheDir.toString()
					)
				)
			}
		)
	}

}