package mihon.desktop.track

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DesktopUnsupportedTrackerTest {

    @Test
    fun `Kitsu is an active desktop tracker rather than an unsupported placeholder`() {
        val tracker: DesktopTracker = KitsuTracker()
        assertFalse(tracker is UnsupportedDesktopTracker)
    }

    @Test
    fun `Komga is an active desktop tracker rather than an unsupported placeholder`() {
        val tracker: DesktopTracker = KomgaTracker()
        assertFalse(tracker is UnsupportedDesktopTracker)
    }

    @Test
    fun `Shikimori is an active desktop tracker rather than an unsupported placeholder`() {
        val tracker: DesktopTracker = ShikimoriTracker()
        assertFalse(tracker is UnsupportedDesktopTracker)
    }

    @Test
    fun `Bangumi and MangaUpdates are active desktop trackers`() {
        val bangumi: DesktopTracker = BangumiTracker()
        val mangaUpdates: DesktopTracker = MangaUpdatesTracker()
        assertFalse(bangumi is UnsupportedDesktopTracker)
        assertFalse(mangaUpdates is UnsupportedDesktopTracker)
    }

    @Test
    fun `Kavita is an active desktop tracker`() {
        val tracker: DesktopTracker = KavitaTracker()
        assertFalse(tracker is UnsupportedDesktopTracker)
    }

    @Test
    fun `Suwayomi is an active desktop tracker`() {
        val tracker: DesktopTracker = SuwayomiTracker()
        assertFalse(tracker is UnsupportedDesktopTracker)
    }

    @Test
    fun `unsupported trackers fail explicitly instead of faking success`() = runBlocking {
        val manager = DesktopTrackerManager()
        val unsupportedIds = emptyList<Long>()

        for (id in unsupportedIds) {
            val tracker = manager.get(id)!!
            assertFalse(
                tracker.login(mapOf("username" to "user", "password" to "secret")),
                "${tracker.name} login should not fake success",
            )
            assertFalse(tracker.isLoggedIn, "${tracker.name} should not be logged in")
            assertFalse(manager.login(id, emptyMap()), "${tracker.name} manager login should fail")

            val track = DesktopTrackRecord(
                mangaId = 1L,
                trackerId = id,
                remoteId = 10L,
                title = "Test",
            )
            val search = runCatching { tracker.search("query") }
            assertTrue(
                search.exceptionOrNull() is TrackerNotSupportedException,
                "${tracker.name} search should throw TrackerNotSupportedException",
            )
            val find = runCatching { tracker.findRemote(track) }
            assertTrue(
                find.exceptionOrNull() is TrackerNotSupportedException,
                "${tracker.name} findRemote should throw TrackerNotSupportedException",
            )
            val update = runCatching { tracker.updateRemote(track) }
            assertTrue(
                update.exceptionOrNull() is TrackerNotSupportedException,
                "${tracker.name} updateRemote should throw TrackerNotSupportedException",
            )
        }
    }

    @Test
    fun `unsupported tracker ignores previously persisted fake credentials`() {
        val tracker = object : UnsupportedDesktopTracker(99L, "Unsupported", TrackerAuthType.TOKEN) {}
        tracker.restoreLogin(
            TrackerLoginInfo(
                trackerId = tracker.id,
                username = "FakeUser",
                token = "fake-token",
            ),
        )

        assertFalse(tracker.isLoggedIn)
        assertEquals(null, tracker.username)
    }
}
