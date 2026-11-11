package mihon.domain.protectedfolder.service

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

object PinLockout {

    const val FREE_ATTEMPTS = 5

    private val BASE_LOCKOUT = 30.seconds

    // 30 seconds doubled twelve times is about 34 hours, which also keeps the shift far from overflowing
    private const val MAX_DOUBLINGS = 12

    /**
     * How long PIN entry stays locked right after the [failedAttempts]th wrong PIN in a row.
     */
    fun lockoutAfter(failedAttempts: Int): Duration {
        if (failedAttempts < FREE_ATTEMPTS) return Duration.ZERO
        val doublings = (failedAttempts - FREE_ATTEMPTS).coerceAtMost(MAX_DOUBLINGS)
        return BASE_LOCKOUT * (1 shl doublings)
    }
}
