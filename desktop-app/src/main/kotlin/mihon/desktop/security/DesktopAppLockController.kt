package mihon.desktop.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.desktop.preferences.DesktopPreferences
import java.security.SecureRandom

const val MIN_PIN_LENGTH = 4
const val MAX_PIN_LENGTH = 64

/** Idle timeout options in minutes. 0 means "never lock automatically". */
val APP_LOCK_TIMEOUT_OPTIONS = listOf(0, 1, 2, 5, 10, 15, 30)

enum class UnlockResult {
    Success,
    WrongPin,
    NotConfigured,
}

enum class ChangePinResult {
    Success,
    WrongCurrentPin,
    InvalidNewPin,
    NotConfigured,
}

/**
 * Owns the desktop app-lock state and persists the salted PIN credential through
 * [DesktopPreferenceStore].
 *
 * The controller is intentionally clock-injectable so idle timeout behavior can be tested without
 * waiting for real time. UI code drives [recordActivity] from pointer/keyboard events and polls
 * [checkIdleTimeout] from a small coroutine ticker.
 */
class DesktopAppLockController(
    private val preferenceStore: DesktopPreferenceStore,
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: SecureRandom = SecureRandom(),
) {

    private var preferences: DesktopPreferences = preferenceStore.load()

    private val _isLocked = MutableStateFlow(
        preferences.appLockEnabled && preferences.appLockOnStartup,
    )

    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private var lastActivityAt: Long = clock()

    val isEnabled: Boolean
        get() = preferences.appLockEnabled

    val lockOnStartup: Boolean
        get() = preferences.appLockOnStartup

    val idleTimeoutMinutes: Int
        get() = preferences.appLockIdleTimeoutMinutes

    @Synchronized
    fun isPinConfigured(): Boolean {
        reloadPreferences()
        return PinHasher.isValidCredential(
            preferences.appLockPinHash,
            preferences.appLockPinSalt,
            preferences.appLockPinIterations,
        )
    }

    @Synchronized
    fun refresh() {
        preferences = preferenceStore.load()
        if (!preferences.appLockEnabled) {
            _isLocked.value = false
        } else if (!_isLocked.value) {
            lastActivityAt = clock()
        }
    }

    /** Locks the app immediately when a PIN is configured and app lock is enabled. */
    @Synchronized
    fun lockNow() {
        reloadPreferences()
        if (preferences.appLockEnabled && isPinConfigured()) {
            _isLocked.value = true
        }
    }

    /** Locks at process start when the user opted into lock-on-startup. */
    @Synchronized
    fun onStartup() {
        reloadPreferences()
        _isLocked.value =
            preferences.appLockEnabled && preferences.appLockOnStartup && isPinConfigured()
        lastActivityAt = clock()
    }

    /**
     * Records pointer/keyboard activity. Activity while the unlock screen is visible is ignored so
     * the lock screen itself cannot keep the app unlocked.
     */
    @Synchronized
    fun recordActivity(now: Long = clock()) {
        if (!preferences.appLockEnabled || _isLocked.value) return
        lastActivityAt = now
    }

    /**
     * Locks the app once the configured idle timeout elapsed. Returns true when this call caused
     * the transition to locked.
     */
    @Synchronized
    fun checkIdleTimeout(now: Long = clock()): Boolean {
        if (!preferences.appLockEnabled || _isLocked.value) return false
        val timeoutMinutes = preferences.appLockIdleTimeoutMinutes
        if (timeoutMinutes <= 0) return false

        if (now < lastActivityAt) {
            // Clock moved backwards (sleep/resume or manual change); treat now as activity.
            lastActivityAt = now
            return false
        }

        val timeoutMillis = timeoutMinutes.toLong() * 60_000L
        if (now - lastActivityAt >= timeoutMillis) {
            _isLocked.value = true
            return true
        }
        return false
    }

    /** Milliseconds until the next automatic lock, or null when automatic locking is disabled. */
    @Synchronized
    fun millisecondsUntilIdleLock(now: Long = clock()): Long? {
        if (!preferences.appLockEnabled || _isLocked.value) return null
        val timeoutMinutes = preferences.appLockIdleTimeoutMinutes
        if (timeoutMinutes <= 0) return null
        val remaining = lastActivityAt + timeoutMinutes.toLong() * 60_000L - now
        return remaining.coerceAtLeast(0L)
    }

    @Synchronized
    fun unlock(pin: String): UnlockResult {
        reloadPreferences()
        if (!preferences.appLockEnabled || !isPinConfigured()) {
            _isLocked.value = false
            return UnlockResult.NotConfigured
        }

        val matches = PinHasher.verify(
            pin = pin,
            hashBase64 = preferences.appLockPinHash,
            saltBase64 = preferences.appLockPinSalt,
            iterations = preferences.appLockPinIterations,
        )
        return if (matches) {
            _isLocked.value = false
            lastActivityAt = clock()
            UnlockResult.Success
        } else {
            UnlockResult.WrongPin
        }
    }

    @Synchronized
    fun verifyPin(pin: String): Boolean {
        reloadPreferences()
        if (!preferences.appLockEnabled || !isPinConfigured()) return false
        return PinHasher.verify(
            pin = pin,
            hashBase64 = preferences.appLockPinHash,
            saltBase64 = preferences.appLockPinSalt,
            iterations = preferences.appLockPinIterations,
        )
    }

    /**
     * Enables app lock and stores a fresh salted hash for [pin]. Returns false when the PIN does
     * not satisfy the minimum requirements.
     */
    @Synchronized
    fun enableWithPin(
        pin: String,
        lockOnStartup: Boolean = preferences.appLockOnStartup,
        idleTimeoutMinutes: Int = preferences.appLockIdleTimeoutMinutes,
    ): Boolean {
        reloadPreferences()
        if (!isValidPin(pin)) return false

        val salt = PinHasher.generateSalt(random)
        val saltBase64 = java.util.Base64.getEncoder().encodeToString(salt)
        val hashBase64 = PinHasher.hashBase64(pin, saltBase64, PinHasher.DEFAULT_ITERATIONS)
            ?: return false

        preferences = preferences.copy(
            appLockEnabled = true,
            appLockPinHash = hashBase64,
            appLockPinSalt = saltBase64,
            appLockPinIterations = PinHasher.DEFAULT_ITERATIONS,
            appLockOnStartup = lockOnStartup,
            appLockIdleTimeoutMinutes = normalizeTimeout(idleTimeoutMinutes),
        )
        preferenceStore.save(preferences)
        _isLocked.value = false
        lastActivityAt = clock()
        return true
    }

    /**
     * Re-enables lock when a valid credential already exists (for example after the setting was
     * turned off without clearing the credential). Returns false when no PIN is configured.
     */
    @Synchronized
    fun enableWithStoredPin(): Boolean {
        reloadPreferences()
        if (!isPinConfigured()) return false
        preferences = preferences.copy(appLockEnabled = true)
        preferenceStore.save(preferences)
        _isLocked.value = false
        lastActivityAt = clock()
        return true
    }

    /**
     * Disables app lock and clears the stored credential. User data (library, downloads,
     * history, ...) is never touched.
     */
    @Synchronized
    fun disableLock() {
        reloadPreferences()
        preferences = preferences.copy(
            appLockEnabled = false,
            appLockPinHash = "",
            appLockPinSalt = "",
            appLockPinIterations = 0,
        )
        preferenceStore.save(preferences)
        _isLocked.value = false
        lastActivityAt = clock()
    }

    @Synchronized
    fun changePin(currentPin: String, newPin: String): ChangePinResult {
        reloadPreferences()
        if (!preferences.appLockEnabled || !isPinConfigured()) return ChangePinResult.NotConfigured
        if (!verifyPin(currentPin)) return ChangePinResult.WrongCurrentPin
        if (!isValidPin(newPin)) return ChangePinResult.InvalidNewPin

        val salt = PinHasher.generateSalt(random)
        val saltBase64 = java.util.Base64.getEncoder().encodeToString(salt)
        val hashBase64 = PinHasher.hashBase64(newPin, saltBase64, PinHasher.DEFAULT_ITERATIONS)
            ?: return ChangePinResult.InvalidNewPin

        preferences = preferences.copy(
            appLockPinHash = hashBase64,
            appLockPinSalt = saltBase64,
            appLockPinIterations = PinHasher.DEFAULT_ITERATIONS,
        )
        preferenceStore.save(preferences)
        lastActivityAt = clock()
        return ChangePinResult.Success
    }

    @Synchronized
    fun setLockOnStartup(enabled: Boolean) {
        reloadPreferences()
        if (preferences.appLockOnStartup == enabled) return
        preferences = preferences.copy(appLockOnStartup = enabled)
        preferenceStore.save(preferences)
    }

    @Synchronized
    fun setIdleTimeoutMinutes(minutes: Int) {
        reloadPreferences()
        val normalized = normalizeTimeout(minutes)
        if (preferences.appLockIdleTimeoutMinutes == normalized) return
        preferences = preferences.copy(appLockIdleTimeoutMinutes = normalized)
        preferenceStore.save(preferences)
        lastActivityAt = clock()
    }

    private fun reloadPreferences() {
        preferences = preferenceStore.load()
    }

    private fun isValidPin(pin: String): Boolean = pin.length in MIN_PIN_LENGTH..MAX_PIN_LENGTH

    private fun normalizeTimeout(minutes: Int): Int {
        if (minutes <= 0) return 0
        return minutes.coerceAtMost(24 * 60)
    }
}
