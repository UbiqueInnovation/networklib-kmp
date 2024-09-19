package ch.ubique.libs.ktor.cache.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver

internal actual class CacheDatabaseDriverFactory actual constructor() {

	actual fun createDriver(cacheName: String): SqlDriver {
		val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
		NetworkCacheDatabase.Schema.create(driver)
		return driver
	}

}
