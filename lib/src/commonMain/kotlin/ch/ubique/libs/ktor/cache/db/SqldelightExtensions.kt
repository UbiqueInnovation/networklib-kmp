package ch.ubique.libs.ktor.cache.db

import app.cash.sqldelight.Query
import app.cash.sqldelight.db.QueryResult

fun <RowType : Any> Query<RowType>.executeUntilFalse(block: (RowType) -> Boolean) {
	execute { cursor ->
		var cont = true
		while (cont && cursor.next().value) {
			cont = block(mapper(cursor))
		}
		QueryResult.Unit
	}
}
