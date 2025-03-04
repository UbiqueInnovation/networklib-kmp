package ch.ubique.libs.ktor.flow

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/**
 * Switches to a new [KtorStateFlow] generated by [transform] whenever the source flow emits a new value.
 *
 * ```
 * // myKtorFlow always refers to the latest KtorStateFlow generated by flatMapLatestToKtorStateFlow
 * val myKtorFlow = someInputFlow.flatMapLatestToKtorStateFlow { input ->
 *     // create a new KtorStateFlow for each input value
 *     ktorStateFlow<Int> { cacheControl ->
 *         client.get("http://example.com/?param=$input") {
 *             cacheControl(cacheControl)
 *         }
 *     }
 * }
 * ```
 *
 * Calling `myKtorFlow.reload()` will reload the latest KtorStateFlow.
 *
 * See also [Flow.flatMapLatest].
 *
 * @param coroutineScope The scope in which the new KtorStateFlow will be created.
 * @param transform A function that takes a value emitted by the source flow and returns a new [KtorStateFlow].
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T, R : RequestState<*>> Flow<T>.flatMapLatestToKtorStateFlow(
	coroutineScope: CoroutineScope = GlobalScope,
	transform: (T) -> KtorStateFlow<R>,
): KtorStateFlow<R> {
	var latestInnerFlow: KtorStateFlow<R>? = null

	val flatMappedFlow = flatMapLatest { value ->
		val innerFlow = transform(value)
		latestInnerFlow = innerFlow
		innerFlow
	}.stateIn(
		coroutineScope,
		SharingStarted.WhileSubscribed(CANCELLATION_DELAY, 0),
		RequestState.Loading as R
	)

	val flatMappedKtorStateFlowWrapper = object : KtorStateFlow<R>(flatMappedFlow) {
		override fun reload() {
			latestInnerFlow?.reload()
		}

		override fun forceReload() {
			latestInnerFlow?.forceReload()
		}
	}

	return flatMappedKtorStateFlowWrapper
}
