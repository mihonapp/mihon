package mihon.desktop.track

import mihon.desktop.platform.CredentialStore
import mihon.desktop.preferences.DesktopPreferenceStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class TrackingRecoveryTest {
    @TempDir lateinit var directory: Path
    private class FakeCredentials(var fail: Boolean = false) : CredentialStore {
        val values = mutableMapOf<String, String>()
        override fun read(target: String) = values[target]
        override fun write(target: String, secret: String): Boolean {
            if (fail) return false
            values[target] = secret
            return true
        }
        override fun delete(target: String): Boolean = values.remove(target) != null
    }

    @Test fun `migration removes plaintext only after verified secure write`() {
        val preferences = DesktopPreferenceStore(directory.resolve("prefs"))
        preferences.update { setProperty("tracker.1.token", "legacy") }
        val credentials = FakeCredentials(true)
        val store = DesktopTrackerStore(preferences, credentials, "test")
        assertEquals("legacy", store.getAuthToken(1))
        assertEquals("legacy", preferences.property("tracker.1.token"))
        credentials.fail = false
        assertEquals("legacy", store.getAuthToken(1))
        assertNull(preferences.property("tracker.1.token"))
        assertEquals("legacy", credentials.values["test/tracker/1"])
    }

    @Test fun `new login never falls back to plaintext on credential failure`() {
        val preferences = DesktopPreferenceStore(directory.resolve("prefs"))
        val store = DesktopTrackerStore(preferences, FakeCredentials(true), "test")
        store.saveLogin(1, "user", "secret")
        assertNull(preferences.property("tracker.1.token"))
        assertEquals("secret", store.getAuthToken(1))
    }

    @Test fun `Windows credential native round trip and delete`() {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getProperty("os.name").startsWith("Windows"))
        val target = "MihonW/test/" + java.util.UUID.randomUUID()
        val credentials = mihon.desktop.platform.WindowsCredentialStore()
        try {
            assertTrue(credentials.write(target, "secret-中文"))
            assertEquals("secret-中文", credentials.read(target))
            assertTrue(credentials.delete(target))
            assertNull(credentials.read(target))
        } finally {
            credentials.delete(target)
        }
    }
}
