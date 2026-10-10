package eu.kanade.tachiyomi.data.download

import eu.kanade.tachiyomi.data.download.model.Download
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.download.service.DownloadPreferences
import java.util.concurrent.ConcurrentHashMap
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
    fun `restarting waits for cancelled download cleanup before resetting chapter state`() = runBlocking {
        withTimeout(5.seconds) {
            coEvery { store.restore() } returns listOf(download)
            every { preferences.parallelSourceLimit.changes() } returns MutableStateFlow(1)
            every { preferences.parallelPageLimit.get() } returns 5
            val downloader = spyk(downloader(), recordPrivateCalls = true)
            downloader.awaitQueueRestored()
            val attempts = AtomicInteger()
            val firstStarted = CompletableDeferred<Unit>()
            val cleanupStarted = CompletableDeferred<Unit>()
            val finishCleanup = CompletableDeferred<Unit>()
            val secondStarted = CompletableDeferred<Download.State>()
            val secondStopped = CompletableDeferred<Unit>()

            coEvery {
                downloader["downloadChapter"](any<Download>(), any<Semaphore>(), any<CompletableDeferred<Unit>>())
            } coAnswers {
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
                downloader.pauseForNetwork("No network")
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
            coEvery { store.restore() } returns listOf(download)
            every { preferences.parallelSourceLimit.changes() } returns MutableStateFlow(1)
            every { preferences.parallelPageLimit.get() } returns 5
            val downloader = spyk(downloader(), recordPrivateCalls = true)
            downloader.awaitQueueRestored()
            val started = CompletableDeferred<Unit>()
            val finishCleanup = CompletableDeferred<Unit>()
            val completed = CompletableDeferred<Unit>()
            every { notifier.onComplete() } answers {
                completed.complete(Unit)
            }
            val attempts = AtomicInteger()
            coEvery {
                downloader["downloadChapter"](any<Download>(), any<Semaphore>(), any<CompletableDeferred<Unit>>())
            } coAnswers {
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
            }
        }
    }

    @Test
    fun `removing a network-paused chapter keeps it removed after cancellation cleanup`() = runBlocking {
        withTimeout(5.seconds) {
            coEvery { store.restore() } returns listOf(download)
            every { preferences.parallelSourceLimit.changes() } returns MutableStateFlow(1)
            every { preferences.parallelPageLimit.get() } returns 5
            every { download.chapter.id } returns 1L
            val downloader = spyk(downloader(), recordPrivateCalls = true)
            downloader.awaitQueueRestored()
            val started = CompletableDeferred<Unit>()
            val finishCleanup = CompletableDeferred<Unit>()
            val stopped = CompletableDeferred<Unit>()
            coEvery {
                downloader["downloadChapter"](any<Download>(), any<Semaphore>(), any<CompletableDeferred<Unit>>())
            } coAnswers {
                download.status = Download.State.DOWNLOADING
                started.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    withContext(NonCancellable) {
                        finishCleanup.await()
                        download.status = Download.State.ERROR
                        stopped.complete(Unit)
                    }
                }
            }

            try {
                assertTrue(downloader.start())
                started.await()
                downloader.pauseForNetwork("No network")
                assertTrue(downloader.isWaitingForNetwork)
                downloader.removeFromQueue(listOf(download.chapter))
                assertTrue(downloader.queueState.value.isEmpty())
                assertFalse(downloader.start())

                finishCleanup.complete(Unit)
                stopped.await()
                assertTrue(downloader.queueState.value.isEmpty())
            } finally {
                finishCleanup.complete(Unit)
                downloader.pause()
            }
        }
    }

    @Test
    fun `removing a running chapter cannot restart past a concurrent network pause`() = runBlocking {
        withTimeout(5.seconds) {
            val remaining = Download(download.source, mockk(), mockk())
            coEvery { store.restore() } returns listOf(download, remaining)
            every { download.chapter.id } returns 1L
            every { remaining.chapter.id } returns 2L
            every { preferences.parallelSourceLimit.changes() } returns MutableStateFlow(1)
            every { preferences.parallelPageLimit.get() } returns 5
            val downloader = spyk(downloader(), recordPrivateCalls = true)
            downloader.awaitQueueRestored()
            val manager = DownloadManager(
                context = mockk(),
                provider = mockk(),
                cache = mockk(),
                getCategories = mockk(),
                getManga = mockk(),
                getChapter = mockk(),
                sourceManager = mockk(),
                downloadPreferences = preferences,
                downloader = downloader,
                pendingDeleter = mockk(),
            )
            val started = CompletableDeferred<Unit>()
            val paused = CompletableDeferred<Unit>()
            val finishRemoval = CompletableDeferred<Unit>()
            val networkCallbackStarted = CompletableDeferred<Unit>()
            coEvery {
                downloader["downloadChapter"](any<Download>(), any<Semaphore>(), any<CompletableDeferred<Unit>>())
            } coAnswers {
                firstArg<Download>().status = Download.State.DOWNLOADING
                started.complete(Unit)
                awaitCancellation()
            }
            every { downloader.pause() } answers {
                callOriginal()
                paused.complete(Unit)
                runBlocking { finishRemoval.await() }
            }
            var removal: Deferred<Unit>? = null
            var networkPause: Deferred<Unit>? = null
            try {
                assertTrue(downloader.start())
                started.await()
                removal = async(Dispatchers.IO) { manager.cancelQueuedDownloads(listOf(download)) }
                paused.await()
                networkPause = async(Dispatchers.IO) {
                    networkCallbackStarted.complete(Unit)
                    downloader.pauseForNetwork("No Wi-Fi")
                }
                networkCallbackStarted.await()
                assertNull(withTimeoutOrNull(100.milliseconds) { networkPause.await() })

                finishRemoval.complete(Unit)
                removal.await()
                networkPause.await()
                assertTrue(downloader.isWaitingForNetwork)
                assertFalse(downloader.isRunning)
                assertEquals(listOf(remaining), downloader.queueState.value)
            } finally {
                finishRemoval.complete(Unit)
                withContext(NonCancellable) {
                    removal?.join()
                    networkPause?.join()
                    downloader.pause()
                }
            }
        }
    }

    @Test
    fun `cancelled restart before dispatch still waits for earlier cleanup`() = runBlocking {
        withTimeout(5.seconds) {
            coEvery { store.restore() } returns listOf(download)
            every { preferences.parallelSourceLimit.changes() } returns MutableStateFlow(1)
            every { preferences.parallelPageLimit.get() } returns 5
            val downloader = spyk(downloader(), recordPrivateCalls = true)
            downloader.awaitQueueRestored()
            val dispatcher = StandardTestDispatcher()
            val controlledScope = CoroutineScope(SupervisorJob() + dispatcher)
            Downloader::class.java.getDeclaredField("scope").apply {
                isAccessible = true
                set(downloader, controlledScope)
            }
            val firstStarted = CompletableDeferred<Unit>()
            val nextStarted = CompletableDeferred<Unit>()
            val finishCleanup = CompletableDeferred<Unit>()
            val cleanupStarted = CompletableDeferred<Unit>()
            val attempts = AtomicInteger()
            coEvery {
                downloader["downloadChapter"](any<Download>(), any<Semaphore>(), any<CompletableDeferred<Unit>>())
            } coAnswers {
                download.status = Download.State.DOWNLOADING
                if (attempts.incrementAndGet() == 1) firstStarted.complete(Unit) else nextStarted.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    withContext(NonCancellable) {
                        cleanupStarted.complete(Unit)
                        finishCleanup.await()
                    }
                }
            }

            try {
                assertTrue(downloader.start())
                dispatcher.scheduler.runCurrent()
                firstStarted.await()
                downloader.pauseForNetwork("No network")
                cleanupStarted.await()
                assertTrue(downloader.start())
                downloader.pauseForNetwork("No network")
                assertTrue(downloader.start())
                dispatcher.scheduler.runCurrent()
                assertNull(withTimeoutOrNull(100.milliseconds) { nextStarted.await() })
            } finally {
                finishCleanup.complete(Unit)
                controlledScope.cancel()
                withContext(NonCancellable) {
                    while (!controlledScope.coroutineContext.job.isCompleted) {
                        dispatcher.scheduler.runCurrent()
                        delay(1.milliseconds)
                    }
                }
            }
        }
    }

    @Test
    fun `overlapping chapters share page slots only within their source`() = runBlocking {
        withTimeout(5.seconds) {
            val nextChapter = Download(download.source, mockk(), mockk())
            val otherSource = Download(mockk(), mockk(), mockk())
            coEvery { store.restore() } returns listOf(download, nextChapter, otherSource)
            every { preferences.parallelSourceLimit.changes() } returns MutableStateFlow(2)
            every { preferences.parallelPageLimit.get() } returns 5
            val downloader = spyk(downloader(), recordPrivateCalls = true)
            downloader.awaitQueueRestored()
            val slotsByDownload = ConcurrentHashMap<Download, Semaphore>()
            val started = CompletableDeferred<Unit>()
            val stopped = CompletableDeferred<Unit>()
            val stoppedCount = AtomicInteger()
            coEvery {
                downloader["downloadChapter"](any<Download>(), any<Semaphore>(), any<CompletableDeferred<Unit>>())
            } coAnswers {
                val chapter = firstArg<Download>()
                chapter.status = Download.State.DOWNLOADING
                slotsByDownload[chapter] = secondArg()
                thirdArg<CompletableDeferred<Unit>>().complete(Unit)
                if (slotsByDownload.size == 3) started.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    if (stoppedCount.incrementAndGet() == 3) stopped.complete(Unit)
                }
            }

            try {
                assertTrue(downloader.start())
                started.await()
                assertSame(slotsByDownload[download], slotsByDownload[nextChapter])
                assertNotSame(slotsByDownload[download], slotsByDownload[otherSource])
                downloader.pause()
                stopped.await()
            } finally {
                downloader.pause()
            }
        }
    }
}
