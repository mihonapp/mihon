package eu.kanade.tachiyomi.data.track

import java.util.UUID
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource

internal class OAuthState(private val timeSource: TimeSource = TimeSource.Monotonic) {
    private var pending: Pending? = null

    @Synchronized
    fun begin(): String {
        val state = UUID.randomUUID().toString()
        pending = Pending(state, timeSource.markNow() + 10.minutes)
        return state
    }

    @Synchronized
    fun consume(state: String?): Boolean {
        val request = pending ?: return false
        if (request.expiresAt.hasPassedNow()) {
            pending = null
            return false
        }
        if (request.state != state) return false

        pending = null
        return true
    }

    private data class Pending(val state: String, val expiresAt: TimeMark)
}
