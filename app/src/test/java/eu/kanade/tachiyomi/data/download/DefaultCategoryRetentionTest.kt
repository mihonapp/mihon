package eu.kanade.tachiyomi.data.download

import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.source.Source
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.manga.model.Manga

/** Public deletion-boundary regression for the 0L correction already proposed in #4182. */
class DefaultCategoryRetentionTest {
    @ParameterizedTest
    @MethodSource("cases")
    fun `category and bookmark exclusions apply before touching downloads`(
        categories: List<Category>,
        exclusions: Set<String>,
        read: Boolean,
        bookmark: Boolean,
        removeBookmarks: Boolean,
        shouldDelete: Boolean,
    ) = runTest {
        val preferences = DownloadPreferences(InMemoryPreferenceStore())
        preferences.removeExcludeCategories.set(exclusions)
        preferences.removeBookmarkedChapters.set(removeBookmarks)
        val getCategories = mockk<GetCategories>()
        val provider = mockk<DownloadProvider>()
        val downloader = mockk<Downloader>(relaxed = true)
        val cache = mockk<DownloadCache>(relaxed = true)
        val pending = mockk<DownloadPendingDeleter>(relaxed = true)
        val manga = Manga.create().copy(id = 101, source = 202, title = "Synthetic retention")
        val chapter = Chapter.create().copy(id = 303, mangaId = manga.id, read = read, bookmark = bookmark)
        val source = mockk<Source>()
        val chapterDir = mockk<UniFile>()
        val mangaDir = mockk<UniFile>()
        coEvery { getCategories.await(manga.id) } returns categories
        coEvery { provider.findChapterDirs(listOf(chapter), manga, source) } returns (mangaDir to listOf(chapterDir))
        every { chapterDir.delete() } returns true
        every { mangaDir.listFiles() } returns arrayOf(mockk())
        val manager = DownloadManager(
            mockk(), provider, cache, getCategories, mockk(), mockk(), mockk(), preferences, downloader, pending,
        )

        manager.deleteChapters(listOf(chapter), manga, source)
        val calls = if (shouldDelete) 1 else 0
        coVerify(exactly = calls) { provider.findChapterDirs(any(), any(), any()) }
        coVerify(exactly = calls) { downloader.dequeue(any()) }
        coVerify(exactly = calls) { cache.removeChapters(any(), any()) }
        verify(exactly = calls) { chapterDir.delete() }

        manager.enqueueChaptersToDelete(listOf(chapter), manga)
        verify { pending.addChapters(if (shouldDelete) listOf(chapter) else emptyList(), manga) }
    }

    companion object {
        @JvmStatic
        fun cases() = listOf(
            Arguments.of(emptyList<Category>(), setOf("0"), true, false, false, false),
            Arguments.of(emptyList<Category>(), setOf("0"), false, false, false, true),
            Arguments.of(emptyList<Category>(), emptySet<String>(), true, false, false, true),
            Arguments.of(listOf(Category(4, "Keep", 0, 0)), setOf("4"), true, false, false, false),
            Arguments.of(listOf(Category(4, "Other", 0, 0)), setOf("0"), true, false, false, true),
            Arguments.of(emptyList<Category>(), emptySet<String>(), true, true, false, false),
            Arguments.of(emptyList<Category>(), emptySet<String>(), true, true, true, true),
        )
    }
}
