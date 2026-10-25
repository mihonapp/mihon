package eu.kanade.tachiyomi.data.download

import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.source.online.HttpSource
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.interactor.GetChapter
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.manga.model.Manga
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class DownloaderHiddenChaptersTest {

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `resume discards a chapter hidden while paused before fetching and preserves completed downloads`() =
        runTest(timeout = 10.seconds) {
            val fixture = fixture()
            val queued = fixture.downloader.queueState.first { it.size == 2 }.first()
            fixture.completed.status = Download.State.DOWNLOADED

            fixture.downloader.pause()
            queued.chapter.hidden shouldBe false
            fixture.currentChapter = queued.chapter.copy(hidden = true)

            fixture.downloader.start() shouldBe true
            fixture.stopped.await()

            fixture.downloader.queueState.value shouldBe listOf(fixture.completed)
            queued.status shouldBe Download.State.NOT_DOWNLOADED
            fixture.completed.status shouldBe Download.State.DOWNLOADED
            coVerify(exactly = 1) { fixture.getChapter.await(queued.chapter.id) }
            coVerify(exactly = 0) { fixture.getChapter.await(fixture.completed.chapter.id) }
            coVerify(exactly = 0) { fixture.provider.getMangaDir(any(), any()) }
            coVerify(exactly = 0) { fixture.source.getPageList(any()) }
            verify { fixture.store.remove(queued) }
            verify(exactly = 0) { fixture.store.remove(fixture.completed) }
        }

    @Test
    fun `resume discards a chapter deleted while paused before fetching`() = runTest(timeout = 10.seconds) {
        val fixture = fixture()
        val queued = fixture.downloader.queueState.first { it.size == 2 }.first()
        fixture.completed.status = Download.State.DOWNLOADED
        fixture.downloader.pause()
        fixture.currentChapter = null

        fixture.downloader.start() shouldBe true
        fixture.stopped.await()

        fixture.downloader.queueState.value shouldBe listOf(fixture.completed)
        queued.status shouldBe Download.State.NOT_DOWNLOADED
        coVerify(exactly = 0) { fixture.provider.getMangaDir(any(), any()) }
        coVerify(exactly = 0) { fixture.source.getPageList(any()) }
    }

    @Test
    fun `resume still attempts a visible queued chapter`() = runTest(timeout = 10.seconds) {
        val fixture = fixture()
        val queued = fixture.downloader.queueState.first { it.size == 2 }.first()
        fixture.completed.status = Download.State.DOWNLOADED
        fixture.downloader.pause()

        fixture.downloader.start() shouldBe true
        fixture.stopped.await()

        queued.status shouldBe Download.State.ERROR
        fixture.downloader.queueState.value shouldBe listOf(queued, fixture.completed)
        fixture.completed.status shouldBe Download.State.DOWNLOADED
        coVerify(exactly = 1) { fixture.provider.getMangaDir(any(), fixture.source) }
        verify(exactly = 0) { fixture.store.remove(queued) }
    }

    private fun TestScope.fixture(): Fixture {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        return Fixture(this)
    }

    private class Fixture(scope: TestScope) {
        val source = mockk<HttpSource>()
        val provider = mockk<DownloadProvider>()
        val getChapter = mockk<GetChapter>()
        val store = mockk<DownloadStore>(relaxUnitFun = true)
        val stopped = CompletableDeferred<Unit>()
        private val notifier = mockk<DownloadNotifier>(relaxUnitFun = true)
        private val downloadPreferences = mockk<DownloadPreferences> {
            // Android preferences emit their current value as soon as changes() is collected.
            every { parallelSourceLimit } returns mockk {
                every { changes() } returns flowOf(5)
            }
            every { parallelPageLimit } returns mockk {
                every { get() } returns 5
            }
        }
        private val manga = Manga.create().copy(id = 1, source = 7)
        private val chapter = Chapter.create().copy(id = 1, mangaId = manga.id)
        val completed = Download(source, manga, chapter.copy(id = 2))

        @Volatile
        var currentChapter: Chapter? = chapter

        init {
            coEvery { store.restore() } returns listOf(Download(source, manga, chapter), completed)
            coEvery { getChapter.await(chapter.id) } answers { currentChapter }
            // A regression reaches this boundary and fails assertions instead of hanging in Android IO.
            coEvery { provider.getMangaDir(any(), any()) } returns Result.failure(
                IllegalStateException("Storage unavailable in test"),
            )
            every { notifier.onComplete() } answers {
                stopped.complete(Unit)
                Unit
            }
        }

        // IO remains the production dispatcher. Awaiting queue restoration and notifier completion
        // synchronizes with that worker; the test dispatcher controls Main and the owning scope.
        val downloader = Downloader(
            scope = scope.backgroundScope,
            context = mockk(),
            provider = provider,
            cache = mockk(),
            sourceManager = mockk(),
            chapterCache = mockk(),
            downloadPreferences = downloadPreferences,
            xml = mockk(),
            getCategories = mockk(),
            getTracks = mockk(),
            store = store,
            notifier = notifier,
            getChapter = getChapter,
        )
    }
}
