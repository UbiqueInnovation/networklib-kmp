package ch.ubique.libs.ktor.cache.db

import app.cash.sqldelight.db.SqlDriver

expect class CacheDatabaseDriverFactory() {
	fun createDriver(cacheName: String): SqlDriver
}
