package mihon.desktop.library.update

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mihon.desktop.extension.DesktopSourceManager
import mihon.desktop.extension.OnlineMangaSyncService
import mihon.desktop.library.db.DesktopLibraryDatabaseFactory
import mihon.desktop.library.model.MangaRecord
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.desktop.preferences.DesktopPreferences
import mihon.extension.source.WindowsCatalogueSource
import mihon.extension.source.model.FilterList
import mihon.extension.source.model.MangasPage
import mihon.extension.source.model.Page
import mihon.extension.source.model.SChapter
import mihon.extension.source.model.SManga
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class LibraryUpdateRecoveryTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `source cancellation is propagated instead of reported as failure`() = runBlocking {
        DesktopLibraryDatabaseFactory.open(directory.resolve("cancel.db")).use { repo ->
            repo.insertManga(MangaRecord(sourceId = 1, url = "/a", title = "a", favorite = true))
            val manager = DesktopSourceManager().apply {
                registerBuiltinSource(Source(1) { throw CancellationException("cancelled") })
            }
            val service = LibraryUpdateService(repo, OnlineMangaSyncService(repo, manager))
            var cancelled = false
            try {
                service.updateLibrary(throttleDelayMs = 0)
            } catch (_: CancellationException) {
                cancelled = true
            }
            assertTrue(cancelled)
        }
    }

    @Test
    fun `another source finishes while one source is blocked and same source stays serial`() = runBlocking {
        DesktopLibraryDatabaseFactory.open(directory.resolve("parallel.db")).use { repo ->
            for ((source, url) in listOf(1L to "/a", 1L to "/b", 2L to "/c")) {
                repo.insertManga(MangaRecord(sourceId = source, url = url, title = url, favorite = true))
            }
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val otherDone = CompletableDeferred<Unit>()
            val calls = java.util.concurrent.atomic.AtomicInteger()
            val manager = DesktopSourceManager().apply {
                registerBuiltinSource(
                    Source(1) {
                        calls.incrementAndGet()
                        entered.complete(Unit)
                        release.await()
                        emptyList()
                    },
                )
                registerBuiltinSource(
                    Source(2) {
                        otherDone.complete(Unit)
                        emptyList()
                    },
                )
            }
            val service = LibraryUpdateService(repo, OnlineMangaSyncService(repo, manager))
            val task = async { service.updateLibrary(throttleDelayMs = 0) }
            val concurrent = try {
                withTimeout(3_000) {
                    entered.await()
                    otherDone.await()
                }
                calls.get() == 1
            } catch (_: Exception) {
                false
            } finally {
                release.complete(Unit)
            }
            assertEquals(3, task.await().totalMangaChecked)
            assertTrue(concurrent)
        }
    }

    @Test
    fun `manual and automatic share lock and restart runs only one missed cycle`() = runBlocking {
        DesktopLibraryDatabaseFactory.open(directory.resolve("restart.db")).use { repo ->
            repo.insertManga(MangaRecord(sourceId = 1, url = "/a", title = "a", favorite = true))
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val calls = java.util.concurrent.atomic.AtomicInteger()
            val manager = DesktopSourceManager().apply {
                registerBuiltinSource(
                    Source(1) {
                        calls.incrementAndGet()
                        entered.complete(Unit)
                        release.await()
                        emptyList()
                    },
                )
            }
            val service = LibraryUpdateService(repo, OnlineMangaSyncService(repo, manager))
            val preferences = DesktopPreferenceStore(directory.resolve("preferences.properties"))
            preferences.save(DesktopPreferences(libraryUpdateIntervalHours = 1))
            val stateFile = directory.resolve("state.properties")
            val scope = CoroutineScope(SupervisorJob())
            try {
                val scheduler = LibraryUpdateScheduler(service, preferences, scope, { 100_000_000L }, stateFile, false)
                val running = async { scheduler.checkAndRunAutoUpdate() }
                withTimeout(3_000) { entered.await() }
                assertNull(scheduler.triggerUpdateNow())
                release.complete(Unit)
                assertNotNull(running.await())
                assertEquals(1, calls.get())
                val restarted = LibraryUpdateScheduler(service, preferences, scope, { 100_000_000L }, stateFile, false)
                assertEquals(LibraryUpdateStatus.COMPLETED, restarted.runState.value.status)
                assertEquals(100_000_000L, restarted.runState.value.lastCompletedEpochMillis)
                assertNull(restarted.checkAndRunAutoUpdate())
                assertEquals(1, calls.get())
            } finally {
                release.complete(Unit)
                scope.cancel()
            }
        }
    }

    @Test
    fun `failed source retries only its failed manga after persisted backoff`() = runBlocking {
        DesktopLibraryDatabaseFactory.open(directory.resolve("retry.db")).use { repo ->
            repo.insertManga(MangaRecord(sourceId = 1, url = "/a", title = "a", favorite = true))
            repo.insertManga(MangaRecord(sourceId = 2, url = "/b", title = "b", favorite = true))
            var failing = true
            val healthyCalls = java.util.concurrent.atomic.AtomicInteger()
            val manager = DesktopSourceManager().apply {
                registerBuiltinSource(
                    Source(1) {
                        if (failing) error("offline")
                        emptyList()
                    },
                )
                registerBuiltinSource(
                    Source(2) {
                        healthyCalls.incrementAndGet()
                        emptyList()
                    },
                )
            }
            val service = LibraryUpdateService(repo, OnlineMangaSyncService(repo, manager))
            val preferences = DesktopPreferenceStore(directory.resolve("retry-preferences.properties"))
            preferences.save(DesktopPreferences(libraryUpdateIntervalHours = 1))
            val stateFile = directory.resolve("retry-state.properties")
            var now = 100_000_000L
            val scope = CoroutineScope(SupervisorJob())
            try {
                val scheduler = LibraryUpdateScheduler(service, preferences, scope, { now }, stateFile, false)
                assertEquals(1, scheduler.checkAndRunAutoUpdate()!!.errors.size)
                assertEquals(LibraryUpdateStatus.FAILED, scheduler.runState.value.status)
                val restarted = LibraryUpdateScheduler(service, preferences, scope, { now }, stateFile, false)
                assertNull(restarted.checkAndRunAutoUpdate())
                now = restarted.runState.value.retryAfterEpochMillis
                failing = false
                assertEquals(1, restarted.checkAndRunAutoUpdate()!!.totalMangaChecked)
                assertEquals(1, healthyCalls.get())
                assertEquals(LibraryUpdateStatus.COMPLETED, restarted.runState.value.status)
                now = 103_600_000L
                failing = true
                assertEquals(2, restarted.checkAndRunAutoUpdate()!!.totalMangaChecked)
                assertEquals(2, healthyCalls.get())
            } finally {
                scope.cancel()
            }
        }
    }

    @Test
    fun `timed out source is reported while healthy source completes`() = runBlocking {
        DesktopLibraryDatabaseFactory.open(directory.resolve("timeout.db")).use { repo ->
            repo.insertManga(MangaRecord(sourceId = 1, url = "/a", title = "a", favorite = true))
            repo.insertManga(MangaRecord(sourceId = 2, url = "/b", title = "b", favorite = true))
            val manager = DesktopSourceManager().apply {
                registerBuiltinSource(Source(1) { kotlinx.coroutines.awaitCancellation() })
                registerBuiltinSource(Source(2) { emptyList() })
            }
            val service = LibraryUpdateService(repo, OnlineMangaSyncService(repo, manager), requestTimeoutMs = 100)
            val report = withTimeout(3_000) { service.updateLibrary(throttleDelayMs = 0) }
            assertEquals(2, report.totalMangaChecked)
            assertEquals(1, report.errors.size)
            assertTrue(report.errors.single().contains("timed out"))
        }
    }

    @Test
    fun `cancel stops remaining manga and persists cancelled state`() = runBlocking {
        DesktopLibraryDatabaseFactory.open(directory.resolve("cancel-scheduler.db")).use { repo ->
            repeat(2) { repo.insertManga(MangaRecord(sourceId = 1, url = "/$it", title = "$it", favorite = true)) }
            val entered = CompletableDeferred<Unit>()
            val calls = java.util.concurrent.atomic.AtomicInteger()
            val manager = DesktopSourceManager().apply {
                registerBuiltinSource(
                    Source(1) {
                        calls.incrementAndGet()
                        entered.complete(Unit)
                        kotlinx.coroutines.awaitCancellation()
                    },
                )
            }
            val service = LibraryUpdateService(repo, OnlineMangaSyncService(repo, manager))
            val preferences = DesktopPreferenceStore(directory.resolve("cancel-preferences.properties"))
            val stateFile = directory.resolve("cancel-state.properties")
            val scope = CoroutineScope(SupervisorJob())
            try {
                val scheduler = LibraryUpdateScheduler(service, preferences, scope, { 100_000_000L }, stateFile, false)
                val running = async { scheduler.triggerUpdateNow() }
                withTimeout(3_000) { entered.await() }
                scheduler.cancelUpdate()
                try {
                    running.await()
                    fail<Unit>("Cancellation should propagate")
                } catch (_: CancellationException) { }
                assertEquals(1, calls.get())
                assertFalse(scheduler.isUpdating.value)
                assertEquals(LibraryUpdateStatus.CANCELLED, scheduler.runState.value.status)
                val restarted = LibraryUpdateScheduler(service, preferences, scope, { 100_000_000L }, stateFile, false)
                assertEquals(LibraryUpdateStatus.CANCELLED, restarted.runState.value.status)
                assertEquals(0L, preferences.load().lastLibraryUpdateEpochMillis)
            } finally {
                scope.cancel()
            }
        }
    }

    @Test
    fun `source concurrency is bounded`() = runBlocking {
        DesktopLibraryDatabaseFactory.open(directory.resolve("bounded.db")).use { repo ->
            val active = java.util.concurrent.atomic.AtomicInteger()
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val manager = DesktopSourceManager()
            for (id in 1L..5L) {
                repo.insertManga(MangaRecord(sourceId = id, url = "/$id", title = "$id", favorite = true))
                manager.registerBuiltinSource(
                    Source(id) {
                        if (active.incrementAndGet() == 3) entered.complete(Unit)
                        try {
                            release.await()
                            emptyList()
                        } finally {
                            active.decrementAndGet()
                        }
                    },
                )
            }
            val service = LibraryUpdateService(repo, OnlineMangaSyncService(repo, manager))
            val running = async { service.updateLibrary(throttleDelayMs = 0) }
            try {
                withTimeout(3_000) { entered.await() }
                assertEquals(3, active.get())
            } finally {
                release.complete(Unit)
            }
            assertEquals(5, running.await().totalMangaChecked)
        }
    }

    private class Source(override val id: Long, val fetch: suspend () -> List<SChapter>) : WindowsCatalogueSource {
        override val name = "Test"
        override val lang = "en"
        override val supportsLatest = true
        override suspend fun getPopularManga(page: Int) = MangasPage(emptyList(), false)
        override suspend fun getLatestUpdates(page: Int) = MangasPage(emptyList(), false)
        override suspend fun searchManga(page: Int, query: String, filters: FilterList) = MangasPage(emptyList(), false)
        override suspend fun getMangaDetails(manga: SManga) = manga
        override suspend fun getChapterList(manga: SManga) = fetch()
        override suspend fun getPageList(chapter: SChapter) = emptyList<Page>()
    }
}
