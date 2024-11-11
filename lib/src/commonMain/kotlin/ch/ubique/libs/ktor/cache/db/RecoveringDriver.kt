package ch.ubique.libs.ktor.cache.db

import app.cash.sqldelight.Query
import app.cash.sqldelight.Transacter
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.IOException

/**
 * A [SqlDriver] wrapper that can recover from fatal errors by recreating the underlying driver.
 *
 * @param driverProvider A function that provides a new [SqlDriver] instance.
 * @param onFatalError A callback that is invoked when a fatal error occurs causing the driver to be recreated.
 */
internal class RecoveringDriver(
	private val driverProvider: () -> SqlDriver,
	private val onFatalError: (error: Throwable) -> Unit,
) : SqlDriver {

	private var driver: SqlDriver = driverProvider()

	private val recoverMutex = Mutex()
	private var isRecovering = false

	private inline fun <R> runRecovering(crossinline block: () -> R): R {
		try {
			return block()
		} catch (e: Exception) {
			return runBlocking {
				// make sure we don't have a race condition where multiple threads try to recover the driver
				isRecovering = true
				recoverMutex.withLock {
					try {
						if (isRecovering) {
							runCatching { driver.close() }.onFailure { it.printStackTrace() }
							onFatalError(e)
							driver = driverProvider()
						}
						block()
					} catch (e: Exception) {
						throw CacheDatabaseException("Unrecoverable cache database error", e)
					} finally {
						isRecovering = false
					}
				}
			}
		}
	}

	override fun currentTransaction(): Transacter.Transaction? = runRecovering {
		driver.currentTransaction()
	}

	override fun execute(
		identifier: Int?,
		sql: String,
		parameters: Int,
		binders: (SqlPreparedStatement.() -> Unit)?,
	): QueryResult<Long> = runRecovering {
		driver.execute(identifier, sql, parameters, binders)
	}

	override fun <R> executeQuery(
		identifier: Int?,
		sql: String,
		mapper: (SqlCursor) -> QueryResult<R>,
		parameters: Int,
		binders: (SqlPreparedStatement.() -> Unit)?,
	): QueryResult<R> = runRecovering {
		driver.executeQuery(identifier, sql, mapper, parameters, binders)
	}

	override fun newTransaction(): QueryResult<Transacter.Transaction> = runRecovering {
		driver.newTransaction()
	}

	override fun addListener(vararg queryKeys: String, listener: Query.Listener) {
		driver.addListener(*queryKeys, listener = listener)
	}

	override fun notifyListeners(vararg queryKeys: String) {
		driver.notifyListeners(*queryKeys)
	}

	override fun removeListener(vararg queryKeys: String, listener: Query.Listener) {
		driver.removeListener(*queryKeys, listener = listener)
	}

	override fun close() {
		driver.close()
	}

}

class CacheDatabaseException(message: String, cause: Throwable? = null) : IOException(message, cause)
