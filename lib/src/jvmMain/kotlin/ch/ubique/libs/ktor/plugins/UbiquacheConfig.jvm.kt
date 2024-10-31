package ch.ubique.libs.ktor.plugins

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import ch.ubique.libs.ktor.cache.db.NetworkCacheDatabase
import ch.ubique.libs.ktor.common.ensureDirectory
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

actual object UbiquacheConfig {

	internal actual fun getCacheDir(cacheName: String): Path {
		val tempDir = System.getProperty("java.io.tmpdir")
		return Path(tempDir, cacheName)
	}

	internal actual fun createDriver(cacheDir: Path): SqlDriver {
		cacheDir.ensureDirectory()
		val driver = JdbcSqliteDriver("jdbc:sqlite:$cacheDir/cache.db")
		runBlocking { NetworkCacheDatabase.Schema.create(driver).await() }
		return driver
	}

}
