package mihon.sync

import android.content.Context
import androidx.work.ExistingWorkPolicy
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import mihon.sync.job.SyncJob

/**
 * Holds the short delay between a user action and the sync it triggers, and exposes it so the UI can
 * show a countdown the user can act on.
 *
 * A visible, interruptible delay beats both extremes: syncing on the very keystroke wastes requests
 * when someone favourites five series in a row, and a silent delay leaves the user unsure whether
 * anything will happen at all. Re-arming on each action collapses a burst into one round.
 */
@Inject
@SingleIn(AppScope::class)
class SyncScheduler(
    private val context: Context,
    private val syncPreferences: SyncPreferences,
) {

    sealed interface State {
        data object Idle : State

        /** Counting down; [secondsLeft] reaches 1 before the sync starts. */
        data class Pending(val secondsLeft: Int) : State

        data object Running : State

        /** Brief confirmation flash before the indicator fades out. */
        data object Finished : State

        data object Failed : State
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private var countdown: Job? = null

    /**
     * Arms, or re-arms, the countdown. Called after any action worth publishing.
     */
    fun schedule() {
        if (!isActive()) return

        countdown?.cancel()
        countdown = scope.launch {
            for (remaining in COUNTDOWN_SECONDS downTo 1) {
                _state.value = State.Pending(remaining)
                delay(1_000)
            }
            start()
        }
    }

    /**
     * Skips the remaining delay, from the indicator's "sync now" action.
     */
    fun syncNow() {
        countdown?.cancel()
        countdown = null
        start()
    }

    /**
     * Runs a pending sync immediately because the app is going away. Without this, closing the app
     * during the countdown would silently drop whatever the user just did.
     */
    fun flushPending() {
        if (_state.value is State.Pending) syncNow()
    }

    /**
     * A round the user asked for directly — refreshing the library, or the button in the settings —
     * is shown until it ends, like one a countdown started. Only the rounds nobody is waiting on stay
     * out of sight.
     */
    fun onSyncRequested() {
        _state.value = State.Running
    }

    // Called by the job itself, so the indicator reflects the real work rather than a guess.

    /**
     * The job ended without a verdict: cancelled, or put off to run later. There is nothing to
     * report, but the indicator must not keep spinning for a round that is no longer running.
     */
    fun onJobStopped() {
        _state.compareAndSet(State.Running, State.Idle)
    }

    /**
     * Rounds nobody is waiting on — the one opening the app starts, the periodic one — finish out of
     * sight, since one runs on every launch. Only a failure is worth showing for those.
     */
    fun onJobFinished(success: Boolean) {
        if (success && _state.value != State.Running) return

        scope.launch {
            _state.value = if (success) State.Finished else State.Failed
            delay(if (success) SUCCESS_FLASH_MS else FAILURE_FLASH_MS)
            // A later action may have re-armed a countdown while the flash was showing; leave it be.
            if (_state.value is State.Finished || _state.value is State.Failed) {
                _state.value = State.Idle
            }
        }
    }

    private fun start() {
        // Nothing is queued while sync is off, and a spinner for a round that will never run would
        // stay up for good.
        val queued = SyncJob.startNow(context, ExistingWorkPolicy.APPEND_OR_REPLACE)
        _state.value = if (queued) State.Running else State.Idle
    }

    private fun isActive() = syncPreferences.isEnabled().get() && syncPreferences.syncOnAction().get()

    companion object {
        const val COUNTDOWN_SECONDS = 30

        private const val SUCCESS_FLASH_MS = 1_800L
        private const val FAILURE_FLASH_MS = 3_000L
    }
}
