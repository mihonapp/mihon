package mihon.desktop.extension

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class DesktopCookieStoreTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `empty store returns null`() {
        val file = tempDir.resolve("cookies.json")
        val store = DesktopCookieStore(file)

        assertNull(store.getCookieHeader("example.com"))
        assertNull(store.getUserAgent("example.com"))
        assertTrue(store.listAll().isEmpty())
    }

    @Test
    fun `stores cookies and matches parent and subdomains`() {
        val file = tempDir.resolve("cookies.json")
        val store = DesktopCookieStore(file)

        store.setCookies(
            domain = "mangadex.org",
            cookies = mapOf("cf_clearance" to "secret123", "session" to "abc"),
            customUserAgent = "CustomUA/1.0",
        )

        // Exact match
        assertEquals("cf_clearance=secret123; session=abc", store.getCookieHeader("mangadex.org"))
        assertEquals("CustomUA/1.0", store.getUserAgent("mangadex.org"))

        // Subdomain match
        assertEquals("cf_clearance=secret123; session=abc", store.getCookieHeader("api.mangadex.org"))
        assertEquals("CustomUA/1.0", store.getUserAgent("uploads.mangadex.org"))

        // Unrelated domain
        assertNull(store.getCookieHeader("other.com"))
        assertNull(store.getUserAgent("other.com"))

        // Verify file persisted
        assertTrue(Files.exists(file))

        // Reload store from disk
        val reloadedStore = DesktopCookieStore(file)
        assertEquals("cf_clearance=secret123; session=abc", reloadedStore.getCookieHeader("mangadex.org"))
        assertEquals("CustomUA/1.0", reloadedStore.getUserAgent("mangadex.org"))
    }

    @Test
    fun `parseRawCookies handles standard and messy cookie strings`() {
        val raw = "cf_clearance=token_xyz;  _ga=GA1.2.345; session_id=9876 ;  "
        val parsed = DesktopCookieStore.parseRawCookies(raw)

        assertEquals(3, parsed.size)
        assertEquals("token_xyz", parsed["cf_clearance"])
        assertEquals("GA1.2.345", parsed["_ga"])
        assertEquals("9876", parsed["session_id"])
    }

    @Test
    fun `removeCookies clears domain and updates disk`() {
        val file = tempDir.resolve("cookies.json")
        val store = DesktopCookieStore(file)

        store.setCookies("example.com", mapOf("key" to "val"))
        assertNotNull(store.getCookieHeader("example.com"))

        store.removeCookies("example.com")
        assertNull(store.getCookieHeader("example.com"))

        val reloaded = DesktopCookieStore(file)
        assertNull(reloaded.getCookieHeader("example.com"))
    }
}
