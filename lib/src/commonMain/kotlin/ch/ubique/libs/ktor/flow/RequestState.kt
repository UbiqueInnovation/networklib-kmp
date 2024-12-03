package ch.ubique.libs.ktor.flow

/**
 * Result state of the [KtorStateFlow].
 *
 * - [Result]: Holds the most recent response.
 * - [Error]: Holds an Exception in case of an error (e.g. request failed), provides a retry function.
 * - [Loading]: Initial state as long we don't have a result yet.
 */
sealed class RequestState<out T : Any?> {
	/**
	 * Result state.
	 * @param data The result.
	 */
	data class Result<T>(val data: T) : RequestState<T>()

	/**
	 * Error state.
	 * @param exception The exception thrown by the request.
	 * @param retry A function that can be invoked to restart the request.
	 */
	data class Error(val exception: Exception, val retry: () -> Unit) : RequestState<Nothing>()

	/**
	 * Loading state.
	 */
	data object Loading : RequestState<Nothing>()

	/**
	 * Get the data if this is a Result state, null otherwise.
	 */
	fun dataIfResult(): T? {
		return (this as? Result)?.data
	}

	/**
	 * Map the data if this is a Result state, unchanged state otherwise.
	 */
	fun <R : Any?> map(resultValueTransform: (T) -> R): RequestState<R> = when (this) {
		is Result -> Result(resultValueTransform(data))
		is Error -> this
		is Loading -> this
	}

	/**
	 * Combine two states.
	 * Returns an Error if at least one is an Error,
	 * a transformed value if both Results are available,
	 * Loading otherwise.
	 */
	fun <O : Any?, R : Any?> combineWith(other: RequestState<O>, resultValuesTransform: (T, O) -> R): RequestState<R> {
		return when {
			this is Error -> return if (other is Error) this.copy { this.retry(); other.retry() } else this
			other is Error -> return other
			this is Result && other is Result -> Result(resultValuesTransform(data, other.data))
			else -> return Loading
		}
	}

}