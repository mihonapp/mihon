package eu.kanade.tachiyomi.data.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.util.system.NetworkState
import eu.kanade.tachiyomi.util.system.activeNetworkState
import eu.kanade.tachiyomi.util.system.networkStateFlow
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import mihon.app.di.AppGraph
import mihon.core.metro.metroGraph
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.domain.download.service.DownloadPreferences
import java.util.UUID

class DownloadJobTest {
    private val context = mockk<Context>(relaxed = true)
    private val manager = mockk<DownloadManager>(relaxed = true)
    private val preferences = mockk<DownloadPreferences>()
    private val requireWifi = MutableStateFlow(false)
    private val network = MutableStateFlow(NetworkState(true, true, true))
    private val queue = MutableStateFlow(listOf(mockk<Download>()))
    private var restored = CompletableDeferred(Unit)
    private var running = false

    @BeforeEach
    fun setUp() {
        mockkStatic(
            "mihon.core.metro.UtilsKt",
            "eu.kanade.tachiyomi.util.system.NetworkStateTrackerKt",
            "eu.kanade.tachiyomi.util.system.WorkManagerExtensionsKt",
        )
        mockkObject(WorkManager.Companion)
        every { WorkManager.getInstance(context) } returns mockk(relaxed = true)

        val graph = mockk<AppGraph>()
        every { context.metroGraph<AppGraph>() } returns graph
        every { graph.inject(any<DownloadJob>()) } answers {
            val worker = firstArg<DownloadJob>()
            mapOf("downloadManager" to manager, "downloadPreferences" to preferences).forEach { (name, value) ->
                DownloadJob::class.java.getDeclaredField(name).apply { isAccessible = true }.set(worker, value)
            }
        }
        coEvery { any<CoroutineWorker>().setForegroundSafely() } just Runs
        every { context.activeNetworkState() } answers { network.value }
        every { context.networkStateFlow() } returns network

        val wifiPreference = mockk<Preference<Boolean>>()
        every { preferences.downloadOnlyOverWifi } returns wifiPreference
        every { wifiPreference.get() } answers { requireWifi.value }
        every { wifiPreference.changes() } returns requireWifi

        every { manager.queueState } returns queue
        every { manager.isRunning } answers { running }
        coEvery { manager.awaitQueueRestored() } coAnswers { restored.await() }
        every { manager.downloaderStart() } answers {
            running = true
            true
        }
        every { manager.downloaderPause() } answers { running = false }
        every { manager.downloaderPauseForNetwork(any()) } answers { running = false }
    }

    @AfterEach
    fun tearDown() {
        DownloadJob.session.stop()
        unmockkAll()
    }

    private fun worker(id: UUID = UUID.randomUUID()): DownloadJob {
        val params = mockk<WorkerParameters>(relaxed = true)
        every { params.id } returns id
        DownloadJob.session.request(id)
        return DownloadJob(context, params)
    }

    @Test
    fun `network mapping requires validated connectivity and honors wifi only`() {
        val offlineStates = listOf(
            NetworkState(false, false, false),
            NetworkState(false, false, true),
            NetworkState(false, true, false),
            NetworkState(false, true, true),
            NetworkState(true, false, false),
            NetworkState(true, false, true),
        )
        for (state in offlineStates) {
            for (wifiOnly in listOf(false, true)) {
                assertEquals(DownloadNetworkStatus.NoNetwork, state.toDownloadNetworkStatus(wifiOnly))
            }
        }
        val mobile = NetworkState(true, true, false)
        val wifi = NetworkState(true, true, true)
        assertEquals(DownloadNetworkStatus.Available, mobile.toDownloadNetworkStatus(false))
        assertEquals(DownloadNetworkStatus.NoWifi, mobile.toDownloadNetworkStatus(true))
        assertEquals(DownloadNetworkStatus.Available, wifi.toDownloadNetworkStatus(false))
        assertEquals(DownloadNetworkStatus.Available, wifi.toDownloadNetworkStatus(true))
    }

    @Test
    fun `offline work waits and resumes when validated connectivity returns`() = runTest {
        network.value = NetworkState(false, false, false)
        val worker = worker()
        val job = launch { worker.doWork() }
        runCurrent()
        verify(exactly = 0) { manager.downloaderStart() }
        assertFalse(running)

        network.value = NetworkState(true, false, true)
        runCurrent()
        verify(exactly = 0) { manager.downloaderStart() }

        network.value = NetworkState(true, true, true)
        runCurrent()
        verify(exactly = 1) { manager.downloaderStart() }
        assertTrue(running)
        job.cancelAndJoin()
    }

    @Test
    fun `wifi preference changes pause and resume work without a network change`() = runTest {
        network.value = NetworkState(true, true, false)
        val worker = worker()
        val job = launch { worker.doWork() }
        runCurrent()
        assertTrue(running)

        requireWifi.value = true
        runCurrent()
        assertFalse(running)

        requireWifi.value = false
        runCurrent()
        assertTrue(running)
        verify(exactly = 2) { manager.downloaderStart() }
        job.cancelAndJoin()
    }

    @Test
    fun `manual pause prevents recovery from restarting the downloader`() = runTest {
        network.value = NetworkState(false, false, false)
        val worker = worker()
        val job = launch { worker.doWork() }
        runCurrent()

        DownloadJob.stop(context)
        network.value = NetworkState(true, true, true)
        runCurrent()
        assertTrue(job.isCompleted)
        verify(exactly = 0) { manager.downloaderStart() }
    }

    @Test
    fun `clearing a waiting queue finishes the worker without a restart`() = runTest {
        network.value = NetworkState(false, false, false)
        val worker = worker()
        val job = launch { worker.doWork() }
        runCurrent()

        queue.value = emptyList()
        DownloadJob.stop(context)
        network.value = NetworkState(true, true, true)
        runCurrent()
        assertTrue(job.isCompleted)
        verify(exactly = 0) { manager.downloaderStart() }
    }

    @Test
    fun `rapid network changes do not restart downloads after cancellation`() = runTest {
        val worker = worker()
        val job = launch { worker.doWork() }
        runCurrent()
        repeat(3) {
            network.value = NetworkState(false, false, false)
            runCurrent()
            assertFalse(running)
            network.value = NetworkState(true, true, true)
            runCurrent()
            assertTrue(running)
        }
        job.cancelAndJoin()
        network.value = NetworkState(false, false, false)
        runCurrent()
        network.value = NetworkState(true, true, true)
        runCurrent()
        verify(exactly = 4) { manager.downloaderStart() }
    }

    @Test
    fun `worker waits for restored downloads before checking an empty queue`() = runTest {
        restored = CompletableDeferred()
        queue.value = emptyList()
        val worker = worker()
        val job = launch { worker.doWork() }
        runCurrent()
        assertFalse(job.isCompleted)
        verify(exactly = 0) { manager.downloaderStart() }

        queue.value = listOf(mockk())
        restored.complete(Unit)
        runCurrent()
        assertTrue(running)
        job.cancelAndJoin()
    }

    @Test
    fun `empty restored queue completes successfully`() = runTest {
        queue.value = emptyList()
        assertEquals(Result.success(), worker().doWork())
        verify(exactly = 0) { manager.downloaderStart() }
    }

    @Test
    fun `requested work includes waiting states and excludes terminal states`() = runTest {
        val workManager = WorkManager.getInstance(context)
        for (state in WorkInfo.State.entries) {
            val info = mockk<WorkInfo>()
            every { info.state } returns state
            every { workManager.getWorkInfosForUniqueWorkFlow(any()) } returns MutableStateFlow(listOf(info))
            val expected = state in listOf(WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED, WorkInfo.State.RUNNING)
            assertEquals(expected, DownloadJob.isRequestedFlow(context).first(), state.name)
        }
        every { workManager.getWorkInfosForUniqueWorkFlow(any()) } returns MutableStateFlow(emptyList())
        assertFalse(DownloadJob.isRequestedFlow(context).first())
    }
}
