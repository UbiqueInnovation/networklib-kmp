package ch.ubique.libs.ktor.plugins

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import ch.ubique.libs.ktor.cache.db.NetworkCacheDatabase
import kotlinx.io.files.Path

actual object UbiquacheConfig {

	internal actual fun getCacheDir(cacheName: String): Path {
		return Path(".")
	}

	internal actual fun createDriver(cacheDir: Path): SqlDriver {
		val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
		NetworkCacheDatabase.Schema.create(driver)
		return driver
	}

}
