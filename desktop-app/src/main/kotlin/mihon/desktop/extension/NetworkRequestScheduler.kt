package mihon.desktop.extension

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mihon.extension.ipc.RequestPriority

/** Limits actual transport requests, including redirects and extension interceptor requests. */
class NetworkRequestScheduler(
    private val totalLimit: Int = 8,
    private val hostLimit: Int = 3,
    private val clock: () -> Long = System::currentTimeMillis,
) : AutoCloseable {
    private class Waiter(val host: String, val owner: String, val priority: Int) {
        val ready = CompletableDeferred<Unit>()
        var granted = false
    }
    private val lock = Any()
    private val waiting = mutableListOf<Waiter>()
    private val active = mutableSetOf<Waiter>()
    private val cooldowns = mutableMapOf<String, Long>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var wakeup: Job? = null
    private var lastOwner: String? = null
    private var urgentStreak = 0
    private var closed = false

    init {
        require(totalLimit > 0 && hostLimit > 0)
    }

    suspend fun <T> withPermit(host: String, owner: String, priority: Int, block: suspend () -> T): T {
        val waiter = Waiter(host, owner, priority.coerceIn(RequestPriority.BACKGROUND, RequestPriority.READER))
        synchronized(lock) {
            check(!closed) { "Network scheduler is closed" }
            waiting.add(waiter)
            drain()
        }
        try {
            waiter.ready.await()
            return block()
        } finally {
            synchronized(lock) {
                waiting.remove(waiter)
                active.remove(waiter)
                drain()
            }
        }
    }

    fun deferHost(host: String, delayMillis: Long) = synchronized(lock) {
        cooldowns[host] = maxOf(cooldowns[host] ?: 0, clock() + delayMillis.coerceIn(0, 86_400_000))
        drain()
    }

    private fun drain() {
        if (closed) return
        wakeup?.cancel()
        wakeup = null
        val now = clock()
        cooldowns.entries.removeIf { it.value <= now }
        while (active.size < totalLimit) {
            val eligible = waiting.filter { waiter ->
                waiter.ready.isActive && waiter.host !in cooldowns &&
                    active.count { it.host == waiter.host } < hostLimit &&
                    // Keep one slot available for reading while downloads are active.
                    (
                        waiter.priority > RequestPriority.BACKGROUND || hostLimit == 1 ||
                            active.count { it.host == waiter.host && it.priority == RequestPriority.BACKGROUND } <
                            hostLimit - 1
                        )
            }
            if (eligible.isEmpty()) break
            val priority = if (urgentStreak >= 4) eligible.minOf { it.priority } else eligible.maxOf { it.priority }
            val group = eligible.filter { it.priority == priority }
            val next = group.firstOrNull { it.owner != lastOwner } ?: group.first()
            waiting.remove(next)
            active.add(next)
            next.granted = true
            lastOwner = next.owner
            urgentStreak = if (priority == RequestPriority.READER) urgentStreak + 1 else 0
            next.ready.complete(Unit)
        }
        val nextWake = waiting.mapNotNull { cooldowns[it.host] }.minOrNull()
        if (nextWake != null) {
            wakeup = scope.launch {
                delay((nextWake - clock()).coerceAtLeast(1))
                synchronized(lock) {
                    wakeup = null
                    drain()
                }
            }
        }
    }

    override fun close() = synchronized(lock) {
        closed = true
        waiting.forEach { it.ready.cancel(CancellationException("Network scheduler closed")) }
        waiting.clear()
        scope.cancel()
    }
}
