package mihon.desktop.track

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class TrackerAndQueueTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `tracker manager contains all default trackers and handles login state`() = runBlocking {
        val manager = DesktopTrackerManager()
        assertEquals(11, manager.trackers.size)

        val mal = manager.get(1L)
        assertNotNull(mal)
        assertEquals("MyAnimeList", mal?.name)
        assertEquals(false, mal?.isLoggedIn)
        // A real tracker refuses an empty token instead of faking a successful login.
        assertEquals(false, mal?.login(emptyMap()))
        assertEquals(false, mal?.isLoggedIn)

        val fakeManager = DesktopTrackerManager(listOf(TrackerTestFakeTracker(1L)))
        assertEquals(true, fakeManager.login(1L, emptyMap()))
        assertEquals(1, fakeManager.loggedInTrackers().size)
        fakeManager.logout(1L)
        assertEquals(false, fakeManager.get(1L)?.isLoggedIn)
    }

    @Test
    fun `offline queue persists updates and recovers state across instances`() = runBlocking {
        val queueFile = tempDir.resolve("tracking-queue.json")
        val queue1 = OfflineTrackingQueue(queueFile)

        val update1 = QueuedTrackingUpdate(mangaId = 1L, trackerId = 1L, chapterNumber = 10.0)
        val update2 = QueuedTrackingUpdate(mangaId = 2L, trackerId = 2L, chapterNumber = 5.0)

        queue1.enqueue(update1)
        queue1.enqueue(update2)

        // Same manga and tracker with higher chapter replaces previous
        val update1Higher = QueuedTrackingUpdate(mangaId = 1L, trackerId = 1L, chapterNumber = 12.0)
        queue1.enqueue(update1Higher)

        val queue2 = OfflineTrackingQueue(queueFile)
        val pending = queue2.peekAll()

        assertEquals(2, pending.size)
        val p1 = pending.find { it.mangaId == 1L }
        assertEquals(12.0, p1?.chapterNumber)

        queue2.remove(p1!!)
        assertEquals(1, queue2.peekAll().size)

        queue2.clear()
        assertTrue(queue2.peekAll().isEmpty())
    }

    @Test
    fun `conflict resolver detects discrepancy and applies resolution policy`() {
        val resolver = TrackingConflictResolver()

        val local = DesktopTrackRecord(
            id = 1L,
            mangaId = 100L,
            trackerId = 1L,
            remoteId = 1001L,
            title = "Test Manga",
            lastChapterRead = 10.0,
            status = TrackStatus.READING.value,
            score = 8.0,
        )

        val remoteIdentical = local.copy()
        assertNull(resolver.detectConflict(local, remoteIdentical, "Test Manga", "MAL"))

        val remoteAhead = local.copy(lastChapterRead = 15.0, score = 9.0)
        val conflict = resolver.detectConflict(local, remoteAhead, "Test Manga", "MAL")
        assertNotNull(conflict)
        assertEquals(10.0, conflict?.localChapterRead)
        assertEquals(15.0, conflict?.remoteChapterRead)

        // Resolve Local Wins
        val localWon = resolver.resolve(local, remoteAhead, ConflictResolutionPolicy.LOCAL_WINS)
        assertEquals(10.0, localWon.lastChapterRead)

        // Resolve Remote Wins
        val remoteWon = resolver.resolve(local, remoteAhead, ConflictResolutionPolicy.REMOTE_WINS)
        assertEquals(15.0, remoteWon.lastChapterRead)
        assertEquals(9.0, remoteWon.score)
    }
}
