package ch.ubique.libs.ktor.plugins

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.worker.WebWorkerDriver
import ch.ubique.libs.ktor.common.isBrowser
import kotlinx.io.files.Path
import org.w3c.dom.Worker


actual object UbiquacheConfig {

    /**
     * Kotlin/JS not implemented
     */
    internal actual fun getCacheDir(cacheName: String): Path {
        if (isBrowser()) {
            throw UnsupportedOperationException("Ubiquache is not supported in the browser")
        }
        return Path("cache")
    }

    /**
     * Kotlin/JS not implemented
     */
    internal actual fun createDriver(cacheDir: Path): SqlDriver {
        return WebWorkerDriver(
            Worker(
                js("""new URL("@cashapp/sqldelight-sqljs-worker/sqljs.worker.js", import.meta.url)""")
            ),
        )
    }

}