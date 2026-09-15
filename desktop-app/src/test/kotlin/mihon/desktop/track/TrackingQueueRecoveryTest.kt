package mihon.desktop.track

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mihon.desktop.library.db.DesktopLibraryDatabaseFactory
import mihon.desktop.library.model.MangaRecord
import mihon.desktop.library.model.TrackingRecord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class TrackingQueueRecoveryTest {
    @TempDir lateinit var directory: Path
    private class Tracker : BaseDesktopTracker(1, "fake") {
        var error: Int? = null
        var calls = 0
        var remoteChapter: Double? = null
        override suspend fun login(
            credentials: Map<String, String>,
        ): Boolean {
            setLoggedIn(true, "user", "token")
            return true
        }
        override suspend fun search(query: String) = emptyList<TrackSearchResult>()
        override suspend fun findRemote(track: DesktopTrackRecord) = track.copy(
            lastChapterRead =
            remoteChapter ?: track.lastChapterRead,
        )
        override suspend fun updateRemote(track: DesktopTrackRecord): DesktopTrackRecord {
            calls++
            delay(10)
            error?.let { throw TrackerHttpException(it) }
            return track
        }
    }

    @Test fun `persisted queue recovers on startup once across concurrent triggers`() = runBlocking {
        val repository = DesktopLibraryDatabaseFactory.open(directory.resolve("db"))
        val manga = repository.insertManga(MangaRecord(sourceId = 1, url = "one", title = "One"))
        repository.insertTracking(
            TrackingRecord(mangaId = manga, trackerId = 1, remoteId = 42, title = "One", lastChapterRead = 5.0),
        )
        OfflineTrackingQueue(directory.resolve("queue")).enqueue(QueuedTrackingUpdate(manga, 1, 5.0))
        val queue = OfflineTrackingQueue(directory.resolve("queue"))
        val tracker = Tracker().apply { restoreLogin(TrackerLoginInfo(1, "user", "token")) }
        val manager = DesktopTrackerManager(listOf(tracker))
        val service = TrackOnReadSyncService(repository, repository, manager, queue, pollMillis = 10)
        try {
            service.start(this)
            service.start(this)
            coroutineScope { repeat(10) { launch { service.flushPending() } } }
            withTimeout(2000) { while (queue.peekAll().isNotEmpty()) delay(10) }
            assertEquals(1, tracker.calls)
        } finally {
            service.close()
            repository.close()
        }
    }

    @Test fun `401 stays paused through restart and login clears pause while 429 backs off`() = runBlocking {
        val repository = DesktopLibraryDatabaseFactory.open(directory.resolve("db"))
        val manga = repository.insertManga(MangaRecord(sourceId = 1, url = "one", title = "One"))
        repository.insertTracking(
            TrackingRecord(mangaId = manga, trackerId = 1, remoteId = 42, title = "One", lastChapterRead = 5.0),
        )
        val queue = OfflineTrackingQueue(directory.resolve("queue"))
        queue.enqueue(QueuedTrackingUpdate(manga, 1, 5.0))
        val tracker = Tracker().apply {
            restoreLogin(TrackerLoginInfo(1, "user", "token"))
            error = 401
        }
        val manager = DesktopTrackerManager(listOf(tracker))
        var time = 1000L
        val service = TrackOnReadSyncService(repository, repository, manager, queue, now = { time })
        try {
            service.flushPending()
            assertTrue(queue.peekAll().single().authenticationRequired)
            time += 100000
            service.flushPending()
            assertEquals(1, tracker.calls)
            tracker.error = 429
            manager.login(1, mapOf("token" to "new"))
            assertEquals(2, tracker.calls)
            assertFalse(queue.peekAll().single().authenticationRequired)
            service.flushPending()
            assertEquals(2, tracker.calls)
            time += 5000
            tracker.error = null
            coroutineScope { repeat(5) { launch { service.flushPending() } } }
            assertEquals(3, tracker.calls)
            assertTrue(queue.peekAll().isEmpty())
        } finally {
            service.close()
            repository.close()
        }
    }

    @Test fun `higher remote progress pauses overwrite until explicit resolution`() = runBlocking {
        val repository = DesktopLibraryDatabaseFactory.open(directory.resolve("db"))
        val manga = repository.insertManga(MangaRecord(sourceId = 1, url = "one", title = "One"))
        repository.insertTracking(
            TrackingRecord(mangaId = manga, trackerId = 1, remoteId = 42, title = "One", lastChapterRead = 5.0),
        )
        val queue = OfflineTrackingQueue(directory.resolve("queue"))
        queue.enqueue(QueuedTrackingUpdate(manga, 1, 5.0))
        val tracker = Tracker().apply {
            restoreLogin(TrackerLoginInfo(1, "user", "token"))
            remoteChapter = 9.0
        }
        val service = TrackOnReadSyncService(repository, repository, DesktopTrackerManager(listOf(tracker)), queue)
        try {
            service.flushPending()
            assertEquals(0, tracker.calls)
            assertEquals(5.0, service.conflicts.value.single().localChapterRead)
            assertEquals(9.0, service.conflicts.value.single().remoteChapterRead)
            service.resolveConflict(manga, 1, ConflictResolutionPolicy.REMOTE_WINS)
            assertEquals(9.0, repository.trackingSnapshot(manga).single().lastChapterRead)
            assertTrue(queue.peekAll().isEmpty())
            assertTrue(service.conflicts.value.isEmpty())
        } finally {
            service.close()
            repository.close()
        }
    }
}
