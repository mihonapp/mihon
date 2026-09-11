package mihon.desktop.track

import kotlinx.coroutines.runBlocking
import mihon.desktop.library.db.DesktopLibraryDatabaseFactory
import mihon.desktop.library.model.MangaRecord
import mihon.desktop.library.model.TrackingRecord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class DesktopOfflineTrackingQueueDrainTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `flushPending drains queued updates into a logged in tracker`() = runBlocking {
        val repo = DesktopLibraryDatabaseFactory.open(tempDir.resolve("flush.db"))
        val mangaId = insertTrackedManga(repo)
        val queue = OfflineTrackingQueue(tempDir.resolve("flush-queue.json"))
        val tracker = TrackerTestFakeTracker(1L)
        val manager = DesktopTrackerManager(listOf(tracker))
        val service = TrackOnReadSyncService(repo, repo, manager, queue)

        // Reading while logged out enqueues the update and leaves the DB current.
        assertEquals(0, service.onChapterRead(mangaId, 12.0))
        assertEquals(1, queue.peekAll().size)

        // Logging the tracker in directly does not notify the manager, so use the explicit flush.
        assertTrue(tracker.login(emptyMap()))
        assertEquals(1, service.flushPending())
        assertTrue(queue.peekAll().isEmpty())
        assertEquals(12.0, tracker.updates.single().lastChapterRead)
        assertEquals(mangaId, tracker.updates.single().mangaId)
        repo.close()
    }

    @Test
    fun `manager login drains pending updates through registered sync service`() = runBlocking {
        val repo = DesktopLibraryDatabaseFactory.open(tempDir.resolve("login-drain.db"))
        val mangaId = insertTrackedManga(repo)
        val queue = OfflineTrackingQueue(tempDir.resolve("login-drain-queue.json"))
        val tracker = TrackerTestFakeTracker(1L)
        val manager = DesktopTrackerManager(listOf(tracker))
        val service = TrackOnReadSyncService(repo, repo, manager, queue)

        assertEquals(0, service.onChapterRead(mangaId, 12.0))
        assertEquals(1, queue.peekAll().size)
        assertEquals(0, manager.syncPending())

        assertTrue(manager.login(1L, mapOf("username" to "User", "password" to "Token")))
        assertTrue(tracker.isLoggedIn)
        assertTrue(queue.peekAll().isEmpty())
        assertEquals(12.0, tracker.updates.single().lastChapterRead)
        repo.close()
    }

    @Test
    fun `flushPending keeps updates for trackers that are not logged in`() = runBlocking {
        val repo = DesktopLibraryDatabaseFactory.open(tempDir.resolve("pending.db"))
        val mangaId = insertTrackedManga(repo)
        val queue = OfflineTrackingQueue(tempDir.resolve("pending-queue.json"))
        val tracker = TrackerTestFakeTracker(1L)
        val manager = DesktopTrackerManager(listOf(tracker))
        val service = TrackOnReadSyncService(repo, repo, manager, queue)

        assertEquals(0, service.onChapterRead(mangaId, 12.0))
        assertEquals(0, service.flushPending())
        assertEquals(1, queue.peekAll().size)
        assertEquals(0, tracker.updates.size)
        repo.close()
    }

    private fun insertTrackedManga(repo: mihon.desktop.library.db.SqlDelightLibraryRepository): Long {
        val mangaId = repo.insertManga(
            MangaRecord(
                sourceId = 100L,
                url = "/manga/offline-${System.nanoTime()}",
                title = "Offline Manga",
            ),
        )
        repo.insertTracking(
            TrackingRecord(
                mangaId = mangaId,
                trackerId = 1L,
                remoteId = 101L,
                title = "Offline Manga",
                lastChapterRead = 10.0,
                totalChapters = 100L,
            ),
        )
        return mangaId
    }
}
