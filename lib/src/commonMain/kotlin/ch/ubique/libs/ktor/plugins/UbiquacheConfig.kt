package ch.ubique.libs.ktor.plugins

import app.cash.sqldelight.db.SqlDriver
import okio.Path

expect object UbiquacheConfig {

	internal fun getCacheDir(cacheName: String): Path

	internal fun createDriver(cacheDir: Path): SqlDriver

}