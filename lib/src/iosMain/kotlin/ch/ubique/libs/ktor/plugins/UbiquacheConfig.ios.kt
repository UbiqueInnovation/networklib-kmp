package ch.ubique.libs.ktor.plugins

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import ch.ubique.libs.ktor.cache.db.NetworkCacheDatabase
import ch.ubique.libs.ktor.common.ensureDirectory
import co.touchlab.sqliter.DatabaseConfiguration
import kotlinx.io.files.Path
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

actual object UbiquacheConfig {

	internal actual fun getCacheDir(cacheName: String): Path {
		val cacheDirectory: String = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true).first() as String
		return Path(cacheDirectory, cacheName)
	}

	internal actual fun createDriver(cacheDir: Path): SqlDriver {
		cacheDir.ensureDirectory()
		return NativeSqliteDriver(
			schema = NetworkCacheDatabase.Schema,
			name = databaseFileName,
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