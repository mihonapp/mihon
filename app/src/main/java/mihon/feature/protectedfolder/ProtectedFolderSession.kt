package mihon.feature.protectedfolder

import android.os.SystemClock
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import mihon.core.metro.AppCoroutineScope
import mihon.domain.protectedfolder.service.ProtectedFolderPreferences
import kotlin.time.Duration.Companion.minutes

/**
 * Whether the protected folder is open. Held in memory only, so the folder is always locked after the process dies.
 */
@Inject
@SingleIn(AppScope::class)
class ProtectedFolderSession(
    @AppCoroutineScope private val scope: CoroutineScope,
    private val preferences: ProtectedFolderPreferences,
) {

    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    @Volatile
    private var lastInteraction = 0L
    private var idleTimerPaused = false
    private var idleTimer: Job? = null

    @Synchronized
    fun unlock() {
        lastInteraction = SystemClock.elapsedRealtime()
        _unlocked.value = true
        startIdleTimer()
    }

    @Synchronized
    fun lock() {
        idleTimer?.cancel()
        _unlocked.value = false
    }

    fun onUserInteraction() {
        lastInteraction = SystemClock.elapsedRealtime()
    }

    /**
     * A chapter is read without touching the screen for longer than the idle timeout, so the reader holds the timer.
     */
    @Synchronized
    fun setIdleTimerPaused(paused: Boolean) {
        idleTimerPaused = paused
        lastInteraction = SystemClock.elapsedRealtime()
        if (paused) {
            idleTimer?.cancel()
        } else if (_unlocked.value) {
            startIdleTimer()
        }
    }

    private fun startIdleTimer() {
        idleTimer?.cancel()
        if (idleTimerPaused) return
        idleTimer = scope.launch {
            while (true) {
                val idleFor = SystemClock.elapsedRealtime() - lastInteraction
                val remaining = preferences.lockAfter.get().minutes.inWholeMilliseconds - idleFor
                if (remaining <= 0) break
                delay(remaining)
            }
            lock()
        }
    }
}
