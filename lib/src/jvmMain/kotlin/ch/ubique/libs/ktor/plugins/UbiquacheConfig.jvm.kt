package ch.ubique.libs.ktor.plugins

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import ch.ubique.libs.ktor.cache.db.NetworkCacheDatabase
import ch.ubique.libs.ktor.common.ensureDirectory
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import kotlinx.io.files.SystemTemporaryDirectory

actual object UbiquacheConfig {

	internal actual fun getCacheDir(cacheName: String): Path {
		return Path(SystemTemporaryDirectory, cacheName)
	}

	internal actual fun createDriver(cacheDir: Path): SqlDriver {
		cacheDir.ensureDirectory()
		val driver = JdbcSqliteDriver("jdbc:sqlite:$cacheDir/$databaseFileName")
		runBlocking { NetworkCacheDatabase.Schema.create(driver).await() }
		return driver
	}

}
