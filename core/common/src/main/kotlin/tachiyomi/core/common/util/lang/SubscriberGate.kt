package tachiyomi.core.common.util.lang

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingCommand
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration

/**
 * Keeps a view model's flows running while its screen is hidden, but holds back database and other frequent
 * updates until the screen is visible again, so returning to it shows the last state instead of re-running
 * everything. Flows collected without a gate in their context are unaffected.
 */
class SubscriberGate : AbstractCoroutineContextElement(SubscriberGate) {

    companion object Key : CoroutineContext.Key<SubscriberGate>

    private val open = MutableStateFlow(true)

    fun whileSubscribed(stopTimeout: Duration): SharingStarted = object : SharingStarted {
        override fun command(subscriptionCount: StateFlow<Int>): Flow<SharingCommand> = flow {
            emit(SharingCommand.START)
            subscriptionCount
                .map { it > 0 }
                .distinctUntilChanged()
                .collectLatest { subscribed ->
                    if (!subscribed) delay(stopTimeout)
                    open.value = subscribed
                }
        }
    }

    internal suspend fun awaitOpen() {
        open.first { it }
    }
}

fun <T> Flow<T>.pausedWhileUnsubscribed(): Flow<T> = flow {
    val gate = currentCoroutineContext()[SubscriberGate]
    if (gate == null) {
        emitAll(this@pausedWhileUnsubscribed)
    } else {
        this@pausedWhileUnsubscribed.conflate().collect {
            gate.awaitOpen()
            emit(it)
        }
    }
}
