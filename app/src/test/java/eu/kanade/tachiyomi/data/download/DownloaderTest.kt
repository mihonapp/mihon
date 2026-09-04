package eu.kanade.tachiyomi.data.download

import androidx.work.WorkManager
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.source.model.Page
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.spyk
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.download.service.DownloadPreferences
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class DownloaderTest {
    private val store = mockk<DownloadStore>(relaxed = true)
    private val notifier = mockk<DownloadNotifier>(relaxed = true)
    private val preferences = mockk<DownloadPreferences>(relaxed = true)
    private val download = Download(mockk(), mockk(), mockk())

    private fun downloader(): Downloader = Downloader(
        context = mockk(),
        provider = mockk(),
        cache = mockk(),
        sourceManager = mockk(),
        chapterCache = mockk(),
        downloadPreferences = preferences,
        xml = mockk(),
        getCategories = mockk(),
        getTracks = mockk(),
        store = store,
        notifier = notifier,
    )

    @Test
    fun `restoration completes only after persisted downloads enter the queue`() = runBlocking {
        withTimeout(5.seconds) {
            val restoreStarted = CompletableDeferred<Unit>()
            val savedDownloads = CompletableDeferred<List<Download>>()
            coEvery { store.restore() } coAnswers {
                restoreStarted.complete(Unit)
                savedDownloads.await()
            }
            val downloader = downloader()
            restoreStarted.await()
            val ready = async { downloader.awaitQueueRestored() }
            assertTrue(downloader.queueState.value.isEmpty())
            assertFalse(ready.isCompleted)

            savedDownloads.complete(listOf(download))
            ready.await()
            assertEquals(listOf(download), downloader.queueState.value)
            assertEquals(Download.State.QUEUE, download.status)
            verify { store.addAll(listOf(download)) }
        }
    }

    @Test
    fun `network pause preserves ready pages and returns active chapters to the queue`() = runBlocking {
        coEvery { store.restore() } returns listOf(download)
        val downloader = downloader()
        downloader.awaitQueueRestored()
        val page = Page(0).apply { status = Page.State.Ready }
        download.pages = listOf(page)
        download.status = Download.State.DOWNLOADING

        downloader.pauseForNetwork("No network")

        assertEquals(Download.State.QUEUE, download.status)
        assertEquals(1, download.downloadedImages)
        assertEquals(listOf(download), downloader.queueState.value)
        assertFalse(downloader.isPaused)
        verify(exactly = 1) { notifier.onWarning("No network") }
        verify(exactly = 0) { notifier.onError(any(), any(), any(), any()) }
    }

    @Test
    fun `restarting waits for cancelled download cleanup before resetting chapter state`() = runBlocking {
        withTimeout(5.seconds) {
            coEvery { store.restore() } returns listOf(download)
            every { preferences.parallelSourceLimit.changes() } returns MutableStateFlow(1)
            val downloader = spyk(downloader(), recordPrivateCalls = true)
            downloader.awaitQueueRestored()
            val attempts = AtomicInteger()
            val firstStarted = CompletableDeferred<Unit>()
            val cleanupStarted = CompletableDeferred<Unit>()
            val finishCleanup = CompletableDeferred<Unit>()
            val secondStarted = CompletableDeferred<Download.State>()
            val secondStopped = CompletableDeferred<Unit>()

            coEvery { downloader["downloadChapter"](any<Download>()) } coAnswers {
                if (attempts.incrementAndGet() == 1) {
                    download.status = Download.State.DOWNLOADING
                    firstStarted.complete(Unit)
                    try {
                        awaitCancellation()
                    } finally {
                        withContext(NonCancellable) {
                            cleanupStarted.complete(Unit)
                            finishCleanup.await()
                            download.status = Download.State.ERROR
                        }
                    }
                } else {
                    secondStarted.complete(download.status)
                    try {
                        awaitCancellation()
                    } finally {
                        secondStopped.complete(Unit)
                    }
                }
            }

            try {
                assertTrue(downloader.start())
                firstStarted.await()
                downloader.pauseForNetwork("No network")
                cleanupStarted.await()
                assertTrue(downloader.start())
                assertNull(withTimeoutOrNull(100.milliseconds) { secondStarted.await() })

                finishCleanup.complete(Unit)
                assertEquals(Download.State.QUEUE, secondStarted.await())
                downloader.pause()
                secondStopped.await()
            } finally {
                finishCleanup.complete(Unit)
                downloader.pause()
            }
        }
    }

    @Test
    fun `chapter completed during cancellation is removed before a new attempt`() = runBlocking {
        withTimeout(5.seconds) {
            mockkObject(WorkManager.Companion)
            every { WorkManager.getInstance(any()) } returns mockk(relaxed = true)
            coEvery { store.restore() } returns listOf(download)
            every { preferences.parallelSourceLimit.changes() } returns MutableStateFlow(1)
            val downloader = spyk(downloader(), recordPrivateCalls = true)
            downloader.awaitQueueRestored()
            val started = CompletableDeferred<Unit>()
            val finishCleanup = CompletableDeferred<Unit>()
            val completed = CompletableDeferred<Unit>()
            every { notifier.onComplete() } answers {
                completed.complete(Unit)
            }
            val attempts = AtomicInteger()
            coEvery { downloader["downloadChapter"](any<Download>()) } coAnswers {
                attempts.incrementAndGet()
                download.status = Download.State.DOWNLOADING
                started.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    withContext(NonCancellable) {
                        finishCleanup.await()
                        download.status = Download.State.DOWNLOADED
                    }
                }
            }

            try {
                assertTrue(downloader.start())
                started.await()
                downloader.pauseForNetwork("No network")
                assertTrue(downloader.start())
                finishCleanup.complete(Unit)

                downloader.queueState.first { it.isEmpty() }
                completed.await()
                assertEquals(1, attempts.get())
                assertEquals(Download.State.DOWNLOADED, download.status)
                assertFalse(downloader.isRunning)
                verify { store.remove(download) }
            } finally {
                finishCleanup.complete(Unit)
                downloader.pause()
                unmockkObject(WorkManager.Companion)
            }
        }
    }
}
