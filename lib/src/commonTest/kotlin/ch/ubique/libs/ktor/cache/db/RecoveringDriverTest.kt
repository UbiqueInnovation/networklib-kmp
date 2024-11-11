package ch.ubique.libs.ktor.cache.db

import ch.ubique.libs.ktor.common.deleteRecursively
import ch.ubique.libs.ktor.plugins.UbiquacheConfig
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlinx.io.files.SystemTemporaryDirectory
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

@OptIn(ExperimentalStdlibApi::class)
class RecoveringDriverTest {

	@Test
	fun fatalError() = runTest {
		withTestingDir { dir ->
			var onFatalErrorCallCount = 0
			val driver = RecoveringDriver(
				driverProvider = { UbiquacheConfig.createDriver(dir) },
				onFatalError = { onFatalErrorCallCount += 1 },
			)
			assertFailsWith(CacheDatabaseException::class) {
				driver.execute(null, "syntax error", 0).await()
			}
			driver.close()
			assertEquals(1, onFatalErrorCallCount)
		}
	}

	@Test
	fun transaction() = runTest {
		withTestingDir { dir ->
			val driver = RecoveringDriver(
				driverProvider = { UbiquacheConfig.createDriver(dir) },
				onFatalError = { },
			)
			val transaction = driver.newTransaction().await()
			val sameTransaction = driver.currentTransaction()
			assertSame(transaction, sameTransaction)
			driver.close()
		}
	}

	@Test
	fun listener() {
		withTestingDir { dir ->
			var listenerCallCount = 0
			val driver = RecoveringDriver(
				driverProvider = { UbiquacheConfig.createDriver(dir) },
				onFatalError = { },
			)
			val listener = { listenerCallCount += 1 }
			val key = "asdf"
			driver.addListener(key, listener = listener)
			driver.notifyListeners(key)
			driver.removeListener(key, listener = listener)
			driver.notifyListeners(key)
			driver.close()
			assertEquals(1, listenerCallCount)
		}
	}

	private inline fun withTestingDir(block: (dir: Path) -> Unit) {
		val dir = Path(SystemTemporaryDirectory, "recovering-driver-" + Random.nextLong().toHexString())
		try {
			block(dir)
		} finally {
			dir.deleteRecursively()
		}
	}

}
