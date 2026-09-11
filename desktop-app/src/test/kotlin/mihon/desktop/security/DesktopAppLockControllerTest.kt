package mihon.desktop.security

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.desktop.preferences.DesktopPreferences
import mihon.desktop.preferences.ThemeMode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class DesktopAppLockControllerTest {

    @TempDir
    lateinit var tempDir: Path

    private fun store(): DesktopPreferenceStore {
        return DesktopPreferenceStore(tempDir.resolve("preferences.properties"))
    }

    @Test
    fun `enabling app lock stores only a salted hash and iteration count`() {
        val store = store()
        val controller = DesktopAppLockController(store, clock = { 0L })

        controller.enableWithPin("1234", lockOnStartup = true, idleTimeoutMinutes = 5) shouldBe true

        val loaded = store.load()
        loaded.appLockEnabled shouldBe true
        loaded.appLockPinHash shouldNotBe "1234"
        loaded.appLockPinSalt shouldNotBe "1234"
        loaded.appLockPinHash.isNotBlank() shouldBe true
        loaded.appLockPinSalt.isNotBlank() shouldBe true
        loaded.appLockPinIterations shouldBe PinHasher.DEFAULT_ITERATIONS
        PinHasher.verify(
            "1234",
            loaded.appLockPinHash,
            loaded.appLockPinSalt,
            loaded.appLockPinIterations,
        ) shouldBe true

        val persistedValues = java.util.Properties().apply {
            Files.newInputStream(tempDir.resolve("preferences.properties")).use { load(it) }
        }.values.map { it.toString() }
        persistedValues.contains("1234") shouldBe false
    }

    @Test
    fun `short pins cannot enable app lock`() {
        val controller = DesktopAppLockController(store(), clock = { 0L })
        controller.enableWithPin("12") shouldBe false
        controller.isEnabled shouldBe false
    }

    @Test
    fun `correct pin unlocks while wrong pin keeps the app locked`() {
        val controller = DesktopAppLockController(store(), clock = { 0L })
        controller.enableWithPin("1234")
        controller.lockNow()
        controller.isLocked.value shouldBe true

        controller.unlock("0000") shouldBe UnlockResult.WrongPin
        controller.isLocked.value shouldBe true

        controller.unlock("1234") shouldBe UnlockResult.Success
        controller.isLocked.value shouldBe false
    }

    @Test
    fun `lock on startup only locks when enabled and configured`() {
        val store = store()
        DesktopAppLockController(store, clock = { 0L }).apply {
            enableWithPin("1234", lockOnStartup = true)
        }

        val restarted = DesktopAppLockController(store, clock = { 0L })
        restarted.isLocked.value shouldBe true

        restarted.unlock("1234") shouldBe UnlockResult.Success
        restarted.isLocked.value shouldBe false
    }

    @Test
    fun `startup does not lock when lock on startup is disabled`() {
        val store = store()
        DesktopAppLockController(store, clock = { 0L }).apply {
            enableWithPin("1234", lockOnStartup = false)
        }

        val restarted = DesktopAppLockController(store, clock = { 0L })
        restarted.onStartup()

        restarted.isLocked.value shouldBe false
    }

    @Test
    fun `idle timeout locks after inactivity and activity resets the timer`() {
        var now = 0L
        val controller = DesktopAppLockController(store(), clock = { now })
        controller.enableWithPin("1234", lockOnStartup = false, idleTimeoutMinutes = 1)
        controller.isLocked.value shouldBe false

        now = 59_999L
        controller.checkIdleTimeout() shouldBe false

        controller.recordActivity()
        now += 59_999L
        controller.checkIdleTimeout() shouldBe false

        now += 1L
        controller.checkIdleTimeout() shouldBe true
        controller.isLocked.value shouldBe true
    }

    @Test
    fun `activity while the lock screen is active does not unlock or extend idle lock`() {
        var now = 0L
        val controller = DesktopAppLockController(store(), clock = { now })
        controller.enableWithPin("1234", lockOnStartup = false, idleTimeoutMinutes = 1)
        controller.lockNow()

        now += 10_000_000L
        controller.recordActivity()
        controller.checkIdleTimeout() shouldBe false
        controller.isLocked.value shouldBe true
    }

    @Test
    fun `never timeout disables automatic locking`() {
        var now = 0L
        val controller = DesktopAppLockController(store(), clock = { now })
        controller.enableWithPin("1234", lockOnStartup = false, idleTimeoutMinutes = 0)

        now += 30L * 24L * 60L * 60L * 1_000L
        controller.checkIdleTimeout() shouldBe false
        controller.isLocked.value shouldBe false
    }

    @Test
    fun `forgot pin path disables lock clears credential and preserves library data`() {
        val store = store()
        store.save(DesktopPreferences(themeMode = ThemeMode.Dark, incognitoMode = true))
        val controller = DesktopAppLockController(store, clock = { 0L })
        controller.enableWithPin("1234")

        controller.disableLock()

        val loaded = store.load()
        loaded.appLockEnabled shouldBe false
        loaded.appLockPinHash shouldBe ""
        loaded.appLockPinSalt shouldBe ""
        loaded.appLockPinIterations shouldBe 0
        loaded.themeMode shouldBe ThemeMode.Dark
        loaded.incognitoMode shouldBe true
        controller.isLocked.value shouldBe false
    }

    @Test
    fun `change pin requires the current pin and rotates the credential`() {
        val controller = DesktopAppLockController(store(), clock = { 0L })
        controller.enableWithPin("1234")
        val oldHash = controller.verifyPin("1234")

        oldHash shouldBe true
        controller.changePin("0000", "5678") shouldBe ChangePinResult.WrongCurrentPin
        controller.verifyPin("1234") shouldBe true

        controller.changePin("1234", "5678") shouldBe ChangePinResult.Success
        controller.verifyPin("5678") shouldBe true
        controller.verifyPin("1234") shouldBe false
        controller.changePin("5678", "12") shouldBe ChangePinResult.InvalidNewPin
    }

    @Test
    fun `timeout preference is normalized and persisted`() {
        val store = store()
        val controller = DesktopAppLockController(store, clock = { 0L })
        controller.enableWithPin("1234", idleTimeoutMinutes = 5)

        controller.setIdleTimeoutMinutes(-3)
        store.load().appLockIdleTimeoutMinutes shouldBe 0
        controller.setIdleTimeoutMinutes(10)
        store.load().appLockIdleTimeoutMinutes shouldBe 10
        controller.setIdleTimeoutMinutes(5_000)
        store.load().appLockIdleTimeoutMinutes shouldBe 24 * 60
    }
}
