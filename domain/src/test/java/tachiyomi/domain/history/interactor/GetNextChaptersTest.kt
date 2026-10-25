package tachiyomi.domain.history.interactor

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.history.repository.HistoryRepository
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository

class GetNextChaptersTest {

    private val chapterRepository = mockk<ChapterRepository>()
    private val mangaRepository = mockk<MangaRepository>()
    private val historyRepository = mockk<HistoryRepository>()
    private val getNextChapters = GetNextChapters(
        GetChaptersByMangaId(chapterRepository),
        GetManga(mangaRepository),
        historyRepository,
    )
    private val manga = Manga.create().copy(id = 1, chapterFlags = Manga.CHAPTER_SORTING_NUMBER)
    private val chapters = listOf(
        chapter(1),
        chapter(2, hidden = true),
        chapter(3),
        chapter(4, read = true),
        chapter(99990000, hidden = true),
    )

    init {
        coEvery { mangaRepository.getMangaById(manga.id) } returns manga
        coEvery { chapterRepository.getChapterByMangaId(manga.id, true) } returns chapters
    }

    @Test
    fun `resume skips hidden and read chapters`() = runTest {
        getNextChapters.await(manga.id).map { it.id } shouldBe listOf(1L, 3L)
    }

    @Test
    fun `including read chapters still excludes hidden chapters`() = runTest {
        getNextChapters.await(manga.id, onlyUnread = false).map { it.id } shouldBe listOf(1L, 3L, 4L)
    }

    @Test
    fun `download ahead skips hidden chapters after current chapter`() = runTest {
        getNextChapters.await(manga.id, 1L).map { it.id } shouldBe listOf(1L, 3L)
    }

    @Test
    fun `hidden history anchor continues forward instead of restarting`() = runTest {
        getNextChapters.await(manga.id, 2L).map { it.id } shouldBe listOf(3L)
        getNextChapters.await(manga.id, 2L, onlyUnread = false).map { it.id } shouldBe listOf(3L, 4L)
    }

    @Test
    fun `read history anchor resumes at the next unread chapter`() = runTest {
        val readChapters = chapters.map { if (it.id == 1L) it.copy(read = true) else it }
        coEvery { chapterRepository.getChapterByMangaId(manga.id, true) } returns readChapters

        getNextChapters.await(manga.id, 1L).map { it.id } shouldBe listOf(3L)
        getNextChapters.await(manga.id, 1L, onlyUnread = false).map { it.id } shouldBe listOf(3L, 4L)
    }

    @Test
    fun `hidden final chapter never wraps around to earlier chapters`() = runTest {
        getNextChapters.await(manga.id, 99990000L) shouldBe emptyList()
        getNextChapters.await(manga.id, 99990000L, onlyUnread = false) shouldBe emptyList()
    }

    @Test
    fun `unhiding restores chapter to resume candidates without changing progress`() = runTest {
        val restored = chapters.map { it.copy(hidden = false) }
        coEvery { chapterRepository.getChapterByMangaId(manga.id, true) } returns restored

        getNextChapters.await(manga.id).map { it.id } shouldBe listOf(1L, 2L, 3L, 99990000L)
    }

    private fun chapter(id: Long, hidden: Boolean = false, read: Boolean = false) = Chapter.create().copy(
        id = id,
        mangaId = manga.id,
        chapterNumber = id.toDouble(),
        hidden = hidden,
        read = read,
    )
}
