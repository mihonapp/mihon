package mihon.domain.protectedfolder.service

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Base64
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@Inject
@SingleIn(AppScope::class)
class ProtectedFolderPin(
    private val preferences: ProtectedFolderPreferences,
) {

    internal var clock: Clock = Clock.System

    val isSet: Boolean
        get() = preferences.pinHash.get().isNotEmpty()

    fun isSetAsFlow(): Flow<Boolean> {
        return preferences.pinHash.changes().map { it.isNotEmpty() }
    }

    /**
     * Stores a salted hash of [pin] and forgets earlier failed attempts.
     *
     * @return false, changing nothing, when [pin] isn't exactly four digits.
     */
    @Synchronized
    fun set(pin: String): Boolean {
        if (!PinHasher.isValidPin(pin)) return false
        val salt = PinHasher.newSalt()
        preferences.pinSalt.set(Base64.getEncoder().encodeToString(salt))
        preferences.pinHash.set(Base64.getEncoder().encodeToString(PinHasher.hash(pin, salt)))
        resetAttempts()
        return true
    }

    @Synchronized
    fun verify(pin: String): Result {
        val remaining = lockoutRemaining()
        if (remaining > Duration.ZERO) return Result.LockedOut(remaining)
        if (!isSet) return Result.Wrong(Duration.ZERO)

        val matches = PinHasher.isValidPin(pin) &&
            PinHasher.verify(
                pin = pin,
                salt = Base64.getDecoder().decode(preferences.pinSalt.get()),
                expectedHash = Base64.getDecoder().decode(preferences.pinHash.get()),
            )
        if (matches) {
            resetAttempts()
            return Result.Success
        }

        val failedAttempts = preferences.failedAttempts.get() + 1
        val lockout = PinLockout.lockoutAfter(failedAttempts)
        preferences.failedAttempts.set(failedAttempts)
        if (lockout > Duration.ZERO) {
            preferences.lockedOutUntil.set(clock.now().toEpochMilliseconds() + lockout.inWholeMilliseconds)
        }
        return Result.Wrong(lockout)
    }

    fun lockoutRemaining(): Duration {
        val remaining = (preferences.lockedOutUntil.get() - clock.now().toEpochMilliseconds()).milliseconds
        // Setting the device clock back must not stretch a lockout past its own length
        return remaining.coerceIn(Duration.ZERO, PinLockout.lockoutAfter(preferences.failedAttempts.get()))
    }

    @Synchronized
    fun clear() {
        preferences.pinHash.delete()
        preferences.pinSalt.delete()
        resetAttempts()
    }

    private fun resetAttempts() {
        preferences.failedAttempts.delete()
        preferences.lockedOutUntil.delete()
    }

    sealed interface Result {
        data object Success : Result

        /**
         * @param lockout how long PIN entry is locked from now on because of this attempt.
         */
        data class Wrong(val lockout: Duration) : Result

        data class LockedOut(val remaining: Duration) : Result
    }
}
