package ch.ubique.libs.ktor.cache.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import co.touchlab.sqliter.DatabaseConfiguration

internal actual class CacheDatabaseDriverFactory actual constructor() {

	actual fun createDriver(cacheName: String): SqlDriver {
		val dir = TODO("cacheDir + '/' + cacheName + '/'")
		return NativeSqliteDriver(
			schema = NetworkCacheDatabase.Schema,
			name = "cache.db",
			onConfiguration = {
				it.copy(
					extendedConfig = DatabaseConfiguration.Extended(
						basePath = dir
					)
				)
			}
		)
	}

}
