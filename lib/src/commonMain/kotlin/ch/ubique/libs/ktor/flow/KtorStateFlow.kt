package ch.ubique.libs.ktor.flow

import ch.ubique.libs.ktor.http.XUbiquache
import ch.ubique.libs.ktor.cache.extensions.backoff
import ch.ubique.libs.ktor.cache.extensions.expiresDate
import ch.ubique.libs.ktor.cache.extensions.nextRefreshDate
import ch.ubique.libs.ktor.cache.extensions.serverDate
import ch.ubique.libs.ktor.flow.RequestState.*
import ch.ubique.libs.ktor.http.throwIfNotSuccessful
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.HttpResponse
import io.ktor.client.utils.CacheControl
import io.ktor.http.HttpHeaders
import io.ktor.util.reflect.TypeInfo
import io.ktor.util.reflect.typeInfo
import io.ktor.utils.io.errors.IOException
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlin.coroutines.CoroutineContext

/**
 * A StateFlow that executes a request repeatedly with an interval given by the response headers.
 * It starts and runs as long it has at least on active collectors.
 * In case of an error the cron stops and has to be restarted manually.
 */
abstract class KtorStateFlow<T>(underlyingStateFlow: StateFlow<T>) : StateFlow<T> by underlyingStateFlow {
	/**
	 * Re-execute the request immediately, may return a cached response. Does nothing if the StateFlow has no active collectors.
	 */
	abstract fun reload()

	/**
	 * Re-execute the request immediately, invalidating the cache. Does nothing if the StateFlow has no active collectors.
	 */
	abstract fun forceReload()
}

/**
 * Builds a StateFlow that has values yielded from the given [request] that executes on a scope tied to the StateFlow observer cycle.
 *
 * The [request] block starts executing when the returned [StateFlow] becomes active.
 * If the [StateFlow] becomes inactive while the [request] block is executing, it
 * will be cancelled after [cancellationTimeout] milliseconds unless the [StateFlow] becomes active again
 * before that timeout (to gracefully handle cases like Activity rotation).
 *
 * After a cancellation, if the [StateFlow] becomes active again, the [request] block will be re-executed
 * from the beginning.
 *
 * @param context The CoroutineContext to run the given request block in. Defaults to [Dispatchers.IO].
 * @param defaultRefreshInterval The fallback refresh interval in millis, used if nothing is specified by the response headers.
 * 			If there is no interval specified by the response headers nor as a fallback, the request is treated as one-shot.
 * @param defaultRefreshBackoff The fallback minimum refresh delay in millis, used if nothing is specified by the response headers.
 * 			Note that `defaultRefreshInterval` takes precedence over this, if neither is specified by the response headers.
 * @param cancellationTimeout The timeout in millis before cancelling the request block if there are no active collectors.
 * @param request The block to run to execute the request when the [StateFlow] has active observers.
 */
inline fun <reified T> ktorStateFlow(
	context: CoroutineContext = Dispatchers.IO,
	defaultRefreshInterval: Long? = null,
	defaultRefreshBackoff: Long = DEFAULT_REFRESH_BACKOFF,
	cancellationTimeout: Long = CANCELLATION_DELAY,
	noinline request: suspend (cacheControl: String?) -> HttpResponse,
): KtorStateFlow<RequestState<T>> =
	ktorStateFlow(
		typeInfo<T>(),
		context,
		defaultRefreshInterval,
		defaultRefreshBackoff,
		cancellationTimeout,
		request
	)

@PublishedApi
internal fun <T> ktorStateFlow(
	typeInfo: TypeInfo,
	context: CoroutineContext,
	defaultRefreshInterval: Long?,
	defaultRefreshBackoff: Long,
	cancellationTimeout: Long,
	request: suspend (cacheControl: String?) -> HttpResponse,
): KtorStateFlow<RequestState<T>> =
	KtorStateFlowImpl(context, cancellationTimeout, MutableStateFlow(Loading)) { forceFresh ->
		try {
			emit(Loading)
			var repeat = true
			if (forceFresh) {
				repeat = loadAndWait(request, typeInfo, CacheControl.NO_CACHE, defaultRefreshInterval, defaultRefreshBackoff)
			} else {
				try {
					loadAndWait(request, typeInfo, CacheControl.ONLY_IF_CACHED, 0, 0)
				} catch (e: ResponseException) {
					// ignore, continue
				} catch (e: IOException) {
					//youtrack.jetbrains.com/issue/KT-7128
				}
			}
			while (repeat) {
				repeat = loadAndWait(request, typeInfo, null, defaultRefreshInterval, defaultRefreshBackoff)
			}
		} catch (e: CancellationException) {
			// ignore
		} catch (e: Exception) {
			emit(Error(e) {
				forceReload()
			})
		}
	}

private suspend fun <T> KtorStateFlowImpl<RequestState<T>>.loadAndWait(
	request: suspend (cacheControl: String?) -> HttpResponse,
	typeInfo: TypeInfo,
	cacheControl: String?,
	defaultRefreshInterval: Long?,
	defaultRefreshBackoff: Long,
): Boolean {
	val response = request(cacheControl)
	if (cacheControl == CacheControl.ONLY_IF_CACHED) {
		if (response.headers[HttpHeaders.XUbiquache]?.contains(CacheControl.ONLY_IF_CACHED) != true) {
			throw IllegalStateException("KtorStateFlow requires the Ubiquache plugin to be installed")
		}
	}
	response.throwIfNotSuccessful()
	emit(Result(response.body(typeInfo)))
	val nextRefresh = response.nextRefreshDate() ?: response.expiresDate()
	val backoff = response.backoff()?.coerceAtLeast(0) ?: defaultRefreshBackoff
	val currentTimeMillis = Clock.System.now().toEpochMilliseconds()
	val minDelay = if (cacheControl == CacheControl.ONLY_IF_CACHED) {
		(backoff - (currentTimeMillis - (response.serverDate() ?: currentTimeMillis))).coerceAtLeast(0)
	} else {
		backoff
	}
	val delay = nextRefresh?.minus(currentTimeMillis)?.coerceAtLeast(minDelay) ?: defaultRefreshInterval
	delay?.let { delay(it) }
	return delay != null
}

@OptIn(ExperimentalCoroutinesApi::class)
private class KtorStateFlowImpl<T>(
	context: CoroutineContext,
	private val cancellationTimeout: Long,
	private val underlyingStateFlow: MutableStateFlow<T>,
	block: suspend KtorStateFlowImpl<T>.(Boolean) -> Unit,
) : KtorStateFlow<T>(underlyingStateFlow) {

	private var blockRunner: StateFlowBlockRunner<T>

	private var hasActiveCollectors = false

	init {
		// use an intermediate supervisor job so that if we cancel individual block runs due to losing
		// observers, it won't cancel the given context as we only cancel w/ the intention of possibly
		// relaunching using the same parent context.
		val supervisorJob = SupervisorJob(context[Job])

		// The scope for this StateFlow where we launch every block Job.
		// The supervisor job is added last to isolate block runs.
		val scope = CoroutineScope(context + supervisorJob)

		blockRunner = StateFlowBlockRunner(this, scope, block)

		scope.launch(Dispatchers.Synchrotron) {
			underlyingStateFlow.subscriptionCount
				.map { subscriptionCount -> subscriptionCount > 0 }
				.distinctUntilChanged()
				.collect { isActive ->
					hasActiveCollectors = isActive
					if (isActive) {
						blockRunner.maybeRun()
					} else {
						blockRunner.cancelWithDelay(cancellationTimeout)
					}
				}
		}
	}

	fun emit(value: T) {
		underlyingStateFlow.value = value
	}

	override fun reload() {
		blockRunner.restart()
	}

	override fun forceReload() {
		blockRunner.restart(true)
	}

	fun hasActiveCollectors(): Boolean {
		return hasActiveCollectors
	}
}

/**
 * A single-threaded dispatcher to synchronize the flow management.
 */
@OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
private val Dispatchers.Synchrotron by lazy { newSingleThreadContext("Synchrotron") }

/**
 * Handles running a block in a coroutine tied to a StateFlow.
 */
@OptIn(ExperimentalCoroutinesApi::class)
private class StateFlowBlockRunner<T>(
	private val stateFlow: KtorStateFlowImpl<T>,
	private val scope: CoroutineScope,
	private val block: suspend KtorStateFlowImpl<T>.(Boolean) -> Unit,
) {
	// currently running block job.
	private var runningJob: Job? = null

	// cancellation job created in cancel.
	private var cancellationJob: Job? = null

	private val pendingForceFresh = atomic(false)

	suspend fun maybeRun(forceFresh: Boolean = false) = withContext(Dispatchers.Synchrotron) {
		cancellationJob?.cancel()
		cancellationJob = null
		if (pendingForceFresh.value) {
			stopRunner()
		} else if (runningJob != null) {
			return@withContext
		}
		runningJob = scope.launch {
			block(stateFlow, forceFresh or pendingForceFresh.getAndSet(false))
		}
	}

	fun restart(forceFresh: Boolean = false) {
		scope.launch(Dispatchers.Synchrotron) {
			if (stateFlow.hasActiveCollectors()) {
				stopRunner()
				maybeRun(forceFresh)
			} else {
				pendingForceFresh.value = true
			}
		}
	}

	suspend fun cancelWithDelay(timeoutInMs: Long) = withContext(Dispatchers.Synchrotron) {
		if (cancellationJob != null) {
			error("Cancel call cannot happen without a maybeRun")
		}
		cancellationJob = scope.launch(Dispatchers.Synchrotron) {
			delay(timeoutInMs)
			if (!stateFlow.hasActiveCollectors()) {
				// one last check on active observers to avoid any race condition between starting
				// a running coroutine and cancellation
				stopRunner()
			}
		}
	}

	private suspend fun stopRunner() = withContext(Dispatchers.Synchrotron) {
		runningJob?.cancel()
		runningJob = null
	}
}
