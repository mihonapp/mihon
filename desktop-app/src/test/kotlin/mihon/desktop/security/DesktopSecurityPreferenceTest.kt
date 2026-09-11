package mihon.desktop.security

import io.kotest.matchers.shouldBe
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.desktop.preferences.DesktopPreferences
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64

class DesktopSecurityPreferenceTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `security settings round trip through the preference store`() {
        val file = tempDir.resolve("preferences.properties")
        val store = DesktopPreferenceStore(file)
        val saltBase64 = Base64.getEncoder().encodeToString(PinHasher.generateSalt())
        val hashBase64 = PinHasher.hashBase64("1234", saltBase64, iterations = 1_000)!!
        val expected = DesktopPreferences(
            appLockEnabled = true,
            appLockPinHash = hashBase64,
            appLockPinSalt = saltBase64,
            appLockPinIterations = 1_000,
            appLockOnStartup = false,
            appLockIdleTimeoutMinutes = 15,
        )

        store.save(expected)

        val loaded = DesktopPreferenceStore(file).load()
        loaded.appLockEnabled shouldBe true
        loaded.appLockPinHash shouldBe hashBase64
        loaded.appLockPinSalt shouldBe saltBase64
        loaded.appLockPinIterations shouldBe 1_000
        loaded.appLockOnStartup shouldBe false
        loaded.appLockIdleTimeoutMinutes shouldBe 15
    }

    @Test
    fun `corrupt pin hash disables app lock and clears the credential fields`() {
        val file = tempDir.resolve("preferences.properties")
        Files.writeString(
            file,
            """
            security.app_lock.enabled=true
            security.app_lock.pin_hash=not-base64!
            security.app_lock.pin_salt=also-not-base64!
            security.app_lock.pin_iterations=210000
            """.trimIndent(),
        )

        val loaded = DesktopPreferenceStore(file).load()

        loaded.appLockEnabled shouldBe false
        loaded.appLockPinHash shouldBe ""
        loaded.appLockPinSalt shouldBe ""
        loaded.appLockPinIterations shouldBe 0
    }

    @Test
    fun `invalid iteration count disables app lock`() {
        val file = tempDir.resolve("preferences.properties")
        val saltBase64 = Base64.getEncoder().encodeToString(PinHasher.generateSalt())
        val hashBase64 = PinHasher.hashBase64("1234", saltBase64, iterations = 1_000)!!
        Files.writeString(
            file,
            """
            security.app_lock.enabled=true
            security.app_lock.pin_hash=$hashBase64
            security.app_lock.pin_salt=$saltBase64
            security.app_lock.pin_iterations=0
            """.trimIndent(),
        )

        val loaded = DesktopPreferenceStore(file).load()

        loaded.appLockEnabled shouldBe false
        loaded.appLockPinHash shouldBe ""
        loaded.appLockPinIterations shouldBe 0
    }

    @Test
    fun `corrupt idle timeout falls back to the default`() {
        val file = tempDir.resolve("preferences.properties")
        Files.writeString(file, "security.app_lock.idle_timeout_minutes=-99\n")

        DesktopPreferenceStore(file).load().appLockIdleTimeoutMinutes shouldBe 5
    }

    @Test
    fun `missing security settings keep safe defaults`() {
        val loaded = DesktopPreferenceStore(tempDir.resolve("preferences.properties")).load()
        loaded.appLockEnabled shouldBe false
        loaded.appLockPinHash shouldBe ""
        loaded.appLockPinSalt shouldBe ""
        loaded.appLockPinIterations shouldBe 0
        loaded.appLockOnStartup shouldBe true
        loaded.appLockIdleTimeoutMinutes shouldBe 5
    }
}
