package mihon.desktop.track

import kotlinx.coroutines.runBlocking
import mihon.desktop.preferences.DesktopPreferenceStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class DesktopTrackerStoreTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `tracker store persists credentials and restores login`() = runBlocking {
        val prefFile = tempDir.resolve("preferences.properties")
        val prefStore = DesktopPreferenceStore(prefFile)
        val trackerStore = DesktopTrackerStore(prefStore)

        assertFalse(trackerStore.isLoggedIn(1L))
        assertNull(trackerStore.getLoginInfo(1L))

        // Save credentials for MAL (trackerId = 1)
        trackerStore.saveLogin(trackerId = 1L, username = "TestUser", token = "mypassword")
        assertTrue(trackerStore.isLoggedIn(1L))
        assertEquals("TestUser", trackerStore.getUsername(1L))
        assertEquals("mypassword", trackerStore.getAuthToken(1L))
        val info = trackerStore.getLoginInfo(1L)
        assertNotNull(info)
        assertEquals("TestUser", info?.username)
        assertEquals("mypassword", info?.token)

        // Save server URL for Komga (trackerId = 6)
        trackerStore.saveLogin(
            trackerId = 6L,
            username = "KomgaAdmin",
            token = "secret-key",
            serverUrl = "https://komga.local:8080",
        )
        assertTrue(trackerStore.isLoggedIn(6L))
        assertEquals("https://komga.local:8080", trackerStore.getServerUrl(6L))

        // Initialize DesktopTrackerManager with trackerStore to verify restore on boot
        val manager = DesktopTrackerManager(store = trackerStore)
        val mal = manager.get(1L)
        assertNotNull(mal)
        assertTrue(mal!!.isLoggedIn)
        assertEquals("TestUser", mal.username)

        val komga = manager.get(6L)
        assertNotNull(komga)
        assertTrue(komga!!.isLoggedIn)
        assertEquals("KomgaAdmin", komga.username)
        assertEquals("https://komga.local:8080", komga.serverUrl)

        // Logout MAL
        manager.logout(1L)
        assertFalse(mal.isLoggedIn)
        assertFalse(trackerStore.isLoggedIn(1L))
        assertNull(trackerStore.getLoginInfo(1L))

        // Login via manager persists to store
        val success = manager.login(
            2L, // AniList
            mapOf("username" to "AniUser", "token" to "oauth-token-12345"),
        )
        assertTrue(success)
        val anilist = manager.get(2L)!!
        assertTrue(anilist.isLoggedIn)
        assertEquals("AniUser", anilist.username)
        assertTrue(trackerStore.isLoggedIn(2L))
        assertEquals("AniUser", trackerStore.getUsername(2L))
        assertEquals("oauth-token-12345", trackerStore.getAuthToken(2L))
    }
}
