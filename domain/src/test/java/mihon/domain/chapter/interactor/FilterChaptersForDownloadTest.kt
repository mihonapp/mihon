package mihon.domain.chapter.interactor

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.history.interactor.GetHistory
import tachiyomi.domain.history.model.History
import tachiyomi.domain.manga.model.Manga

class FilterChaptersForDownloadTest {

    private val getChaptersByMangaId = mockk<GetChaptersByMangaId>()
    private val downloadPreferences = mockk<DownloadPreferences>()
    private val getCategories = mockk<GetCategories>()
    private val getHistory = mockk<GetHistory>()

    private val filter = FilterChaptersForDownload(
        getChaptersByMangaId = getChaptersByMangaId,
        downloadPreferences = downloadPreferences,
        getCategories = getCategories,
        getHistory = getHistory,
    )

    @Test
    fun `new chapter downloads regardless of the user's chapter list sort mode`() = runTest {
        // Chapters sorted alphabetically would put "Chapter 46" before "Chapter 5", so a
        // window computed from the user's sort order misses the new chapter entirely.
        val manga = manga().copy(chapterFlags = Manga.CHAPTER_SORTING_ALPHABET)
        val allChapters = (1..45).map { number ->
            chapter(number, read = true, sourceOrder = (45 - number).toLong())
                .copy(name = "Chapter $number")
        } + chapter(46, read = false, sourceOrder = 0).copy(name = "Chapter 46")
        val newChapter = allChapters.last()

        every { downloadPreferences.downloadNewChapters } returns pref(true)
        every { downloadPreferences.downloadNewUnreadChaptersOnly } returns pref(false)
        every { downloadPreferences.downloadNewReadingWindow } returns pref(5)
        every { downloadPreferences.downloadNewChapterCategories } returns pref(emptySet())
        every { downloadPreferences.downloadNewChapterCategoriesExclude } returns pref(emptySet())
        coEvery { getCategories.await(manga.id) } returns emptyList()
        coEvery { getHistory.await(manga.id) } returns listOf(history(45))
        coEvery { getChaptersByMangaId.await(manga.id) } returns allChapters
        coEvery { getChaptersByMangaId.await(manga.id, applyScanlatorFilter = true) } returns allChapters

        val result = filter.await(manga, listOf(newChapter))

        result shouldBe listOf(newChapter)
    }

    @Test
    fun `new chapter downloads when all previous chapters are read`() = runTest {
        val manga = manga()
        // 45 chapters read, chapter 46 is new (unread). Source order: newest first.
        val allChapters = (1..45).map { chapter(it, read = true, sourceOrder = (45 - it).toLong()) } +
            chapter(46, read = false, sourceOrder = 0)
        val newChapter = allChapters.last()

        every { downloadPreferences.downloadNewChapters } returns pref(true)
        every { downloadPreferences.downloadNewUnreadChaptersOnly } returns pref(false)
        every { downloadPreferences.downloadNewReadingWindow } returns pref(5)
        every { downloadPreferences.downloadNewChapterCategories } returns pref(emptySet())
        every { downloadPreferences.downloadNewChapterCategoriesExclude } returns pref(emptySet())
        coEvery { getCategories.await(manga.id) } returns emptyList()
        coEvery { getHistory.await(manga.id) } returns listOf(history(45))
        coEvery { getChaptersByMangaId.await(manga.id) } returns allChapters
        coEvery { getChaptersByMangaId.await(manga.id, applyScanlatorFilter = true) } returns allChapters

        val result = filter.await(manga, listOf(newChapter))

        result shouldBe listOf(newChapter)
    }

    private fun manga(): Manga {
        return Manga.create().copy(id = 1L, favoriteAt = kotlin.time.Instant.fromEpochMilliseconds(0), title = "Manga")    }

    private fun chapter(number: Int, read: Boolean, sourceOrder: Long): Chapter {
        return Chapter.create().copy(
            id = number.toLong(),
            mangaId = 1L,
            chapterNumber = number.toDouble(),
            read = read,
            sourceOrder = sourceOrder,
        )
    }

    private fun history(chapterId: Long): History {
        return History.create().copy(id = chapterId, chapterId = chapterId)
    }

    private fun <T> pref(value: T) = mockk<tachiyomi.core.common.preference.Preference<T>> {
        every { get() } returns value
    }
}
