package mihon.domain.source.interactor

import eu.kanade.domain.chapter.interactor.SyncChaptersWithSource
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaRemoteUpdate
import tachiyomi.domain.manga.repository.MangaRepository
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

class UpdateMangaFromRemoteTest {
    private val manga = Manga.create().copy(id = 1, source = 1, url = "/manga", title = "Manga")
    private var storedManga = manga
    private val source = mockk<Source>()
    private val chapterRepository = mockk<ChapterRepository>()
    private val mangaRepository = mockk<MangaRepository>()
    private val syncChapters = mockk<SyncChaptersWithSource>()
    private val coverCache = mockk<CoverCache>()
    private val update = UpdateMangaFromRemote(
        sourceManager = mockk(),
        chapterRepository = chapterRepository,
        mangaRepository = mangaRepository,
        syncChaptersWithSource = syncChapters,
        coverCache = coverCache,
        libraryPreferences = mockk(),
        downloadManager = mockk(relaxed = true),
    )

    init {
        coEvery { chapterRepository.getChapterByMangaId(manga.id) } returns emptyList()
        coEvery { mangaRepository.getMangaById(manga.id) } coAnswers { storedManga }
        coEvery { mangaRepository.updateRemote(any()) } coAnswers {
            val change = firstArg<MangaRemoteUpdate>()
            storedManga = storedManga.copy(
                description = change.description,
                initialized = change.initialized,
                coverLastModified = change.coverLastModified ?: storedManga.coverLastModified,
                thumbnailUrl = change.thumbnailUrl ?: storedManga.thumbnailUrl,
            )
            true
        }
    }

    @Test
    fun manualDetailsRefreshInvalidatesTheCoverEvenWhenItsUrlIsUnchanged() = runTest {
        val mangaWithCover = manga.copy(thumbnailUrl = "https://example.org/cover.jpg")
        storedManga = mangaWithCover
        coEvery { source.getMangaUpdate(any(), emptyList(), true, false) } coAnswers {
            SMangaUpdate(firstArg(), emptyList())
        }
        every { coverCache.hasCustomCover(mangaWithCover.id) } returns false
        every { coverCache.deleteFromCache(mangaWithCover, false) } returns 1

        val result = update(source, mangaWithCover, fetchDetails = true, manualFetch = true).getOrThrow()

        result.manga.thumbnailUrl shouldBe mangaWithCover.thumbnailUrl
        (result.manga.coverLastModified != null) shouldBe true
        result.newChapters shouldBe emptyList()
        verify(exactly = 1) { coverCache.deleteFromCache(mangaWithCover, false) }
        coVerify(exactly = 0) { syncChapters.await(any(), any(), any(), any(), any()) }
    }

    @Test
    fun savesDetailsBeforeRequestingChaptersAndKeepsThemIfChaptersFail() = runTest {
        val failure = IOException("Chapter request failed")
        coEvery { source.getMangaUpdate(any(), emptyList(), true, false) } coAnswers {
            SMangaUpdate(firstArg<SManga>().apply { description = "Synopsis" }, emptyList())
        }
        coEvery { source.getMangaUpdate(any(), emptyList(), false, true) } coAnswers {
            storedManga.description shouldBe "Synopsis"
            storedManga.initialized shouldBe true
            firstArg<SManga>().description shouldBe "Synopsis"
            throw failure
        }

        val result = update(source, manga, fetchDetails = true, fetchChapters = true)

        result.exceptionOrNull() shouldBe failure
        storedManga.description shouldBe "Synopsis"
        storedManga.initialized shouldBe true
        coVerify(exactly = 0) { syncChapters.await(any(), any(), any(), any(), any()) }
    }

    @Test
    fun respectsSourcesThatRejectConcurrentRequestsForTheSameManga() = runTest {
        val requestActive = AtomicBoolean(false)
        val sourceChapter = SChapter.create().apply { url = "/chapter/1" }
        val chapter = Chapter.create().copy(url = sourceChapter.url)
        coEvery { source.getMangaUpdate(any(), emptyList(), any(), any()) } coAnswers {
            check(requestActive.compareAndSet(false, true)) { "Concurrent requests for the same manga" }
            try {
                delay(10)
                val remoteManga = firstArg<SManga>().apply { description = "Synopsis" }
                val remoteChapters = if (arg<Boolean>(3)) listOf(sourceChapter) else emptyList()
                SMangaUpdate(remoteManga, remoteChapters)
            } finally {
                requestActive.set(false)
            }
        }
        coEvery { syncChapters.await(listOf(sourceChapter), any(), source, false, null) } returns listOf(chapter)

        val result = update(source, manga, fetchDetails = true, fetchChapters = true).getOrThrow()

        result.manga.description shouldBe "Synopsis"
        result.newChapters shouldBe listOf(chapter)
    }

    @Test
    fun fetchesStoredChapterListsInOneRequest() = runTest {
        val chapter = Chapter.create().copy(url = "/chapter/1", name = "Chapter 1")
        coEvery { chapterRepository.getChapterByMangaId(manga.id) } returns listOf(chapter)
        coEvery { source.getMangaUpdate(any(), any(), true, true) } coAnswers {
            SMangaUpdate(firstArg(), secondArg())
        }
        coEvery { syncChapters.await(any(), manga, source, false, null) } returns emptyList()

        update(source, manga, fetchDetails = true, fetchChapters = true).getOrThrow()

        coVerify(exactly = 1) { source.getMangaUpdate(any(), any(), any(), any()) }
    }

    @Test
    fun fallsBackToCombinedFetchIfEarlyDetailsFail() = runTest {
        val sourceChapter = SChapter.create().apply { url = "/chapter/1" }
        val chapter = Chapter.create().copy(url = sourceChapter.url)
        coEvery { source.getMangaUpdate(any(), emptyList(), true, false) } throws IOException("Details request failed")
        coEvery { source.getMangaUpdate(any(), emptyList(), true, true) } coAnswers {
            SMangaUpdate(firstArg<SManga>().apply { description = "Synopsis" }, listOf(sourceChapter))
        }
        coEvery { syncChapters.await(listOf(sourceChapter), manga, source, false, null) } returns listOf(chapter)

        val result = update(source, manga, fetchDetails = true, fetchChapters = true).getOrThrow()

        result.manga.description shouldBe "Synopsis"
        result.newChapters shouldBe listOf(chapter)
    }
}
