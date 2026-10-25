package eu.kanade.tachiyomi.data.download

import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.source.Source
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.source.local.LocalSource
import java.io.IOException

class DownloadHiddenChaptersTest {
    private val provider = mockk<DownloadProvider>()
    private val downloader = mockk<Downloader>(relaxUnitFun = true)
    private val cache = mockk<DownloadCache>(relaxUnitFun = true)

    private val settings = DownloadPreferences(InMemoryPreferenceStore()).apply {
        removeBookmarkedChapters.set(true)
    }
    private val preferences = mockk<DownloadPreferences> {
        every { removeBookmarkedChapters } returns settings.removeBookmarkedChapters
        every { removeExcludeCategories } returns settings.removeExcludeCategories
    }
    private val categories = mockk<GetCategories> { coEvery { await(any()) } returns emptyList() }
    private val source = mockk<Source> { every { id } returns 7 }
    private val manga = Manga.create().copy(id = 1, source = 7)
    private val target = Chapter.create().copy(id = 2, mangaId = 1, bookmark = true, read = true)
    private val sibling = target.copy(id = 3, name = "Sibling")
    private val snapshot = listOf(target, sibling)
    private val repository = mockk<ChapterRepository> {
        coEvery { getChapterByMangaId(manga.id, false) } returns snapshot
    }
    private val mangas = mockk<MangaRepository> { coEvery { getAll() } returns listOf(manga) }

    init {
        coEvery { provider.findMangaIdsSharingDownloadDirectory(any(), any(), any()) } returns setOf(manga.id)
    }

    private val manager = DownloadManager(
        mockk(), provider, cache, categories, mockk(), mockk(), repository, mockk(), preferences, downloader,
        mockk(), mangas,
    )

    @Test
    fun `hide deletes eligible target final and partial paths only after writers finish`() = runTest {
        val final = mockk<UniFile> { every { delete() } returns true }
        val partial = mockk<UniFile> { every { delete() } returns true }
        val unrelated = mockk<UniFile>()
        coEvery { provider.findChapterPathsForHide(listOf(target), snapshot, manga, source) } returns
            listOf(final, partial)

        manager.deleteHiddenChapters(listOf(target), manga, source)

        coVerifyOrder {
            downloader.dequeueAndAwait(listOf(target))
            repository.getChapterByMangaId(manga.id, false)
            provider.findChapterPathsForHide(listOf(target), snapshot, manga, source)
            final.delete()
            partial.delete()
            cache.refreshMangaDirectories(any())
        }
        verify(exactly = 0) { unrelated.delete() }
        verify(exactly = 0) { cache.invalidateCache() }
        coVerify(exactly = 1) { cache.refreshMangaDirectories(listOf(manga)) }
        coVerify(exactly = 0) { cache.removeChapters(any(), any()) }
    }

    @Test
    fun `refresh failure does not replace the original deletion failure`() = runTest {
        val deletionFailure = IOException("Deletion failed")
        val refreshFailure = IOException("Refresh failed")
        val final = mockk<UniFile> { every { delete() } throws deletionFailure }
        coEvery { provider.findChapterPathsForHide(any(), any(), any(), any()) } returns listOf(final)
        coEvery { cache.refreshMangaDirectories(any()) } throws refreshFailure

        val thrown = assertThrows<IOException> { manager.deleteHiddenChapters(listOf(target), manga, source) }

        // Coroutine stack-trace recovery can copy an exception while retaining its original as the cause.
        val failures = generateSequence<Throwable>(thrown) { it.cause }.toList()
        assertEquals("Deletion failed", thrown.message)
        assertTrue(failures.any { it === deletionFailure })
        assertTrue(failures.any { refreshFailure in it.suppressed })
        verify(exactly = 0) { cache.invalidateCache() }
    }

    @Test
    fun `navigation cancellation after deletion still refreshes the affected cache`() = runTest {
        lateinit var hide: Job
        val final = mockk<UniFile> {
            every { delete() } answers {
                hide.cancel()
                true
            }
        }
        coEvery { provider.findChapterPathsForHide(any(), any(), any(), any()) } returns listOf(final)
        hide = launch(start = CoroutineStart.LAZY) { manager.deleteHiddenChapters(listOf(target), manga, source) }

        hide.start()
        hide.join()

        coVerify(exactly = 1) { cache.refreshMangaDirectories(listOf(manga)) }
        verify(exactly = 0) { cache.invalidateCache() }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `hide respects allow deleting bookmarked chapters`(allowDeletingBookmarks: Boolean) = runTest {
        settings.removeBookmarkedChapters.set(allowDeletingBookmarks)
        val downloaded = mockk<UniFile> { every { delete() } returns true }
        coEvery { provider.findChapterPathsForHide(any(), any(), any(), any()) } returns listOf(downloaded)

        manager.deleteHiddenChapters(listOf(target), manga, source)

        coVerify(exactly = 1) { downloader.dequeueAndAwait(listOf(target)) }
        coVerify(exactly = if (allowDeletingBookmarks) 1 else 0) {
            provider.findChapterPathsForHide(listOf(target), snapshot, manga, source)
        }
        coVerify(exactly = if (allowDeletingBookmarks) 1 else 0) {
            provider.findMangaIdsSharingDownloadDirectory(any(), any(), any())
        }
        verify(exactly = if (allowDeletingBookmarks) 1 else 0) { downloaded.delete() }
        coVerify(exactly = if (allowDeletingBookmarks) 1 else 0) { cache.refreshMangaDirectories(any()) }
        coVerify(exactly = 0) { cache.removeChapters(any(), any()) }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `excluded categories protect read chapters but still delete unread chapters`(read: Boolean) = runTest {
        settings.removeExcludeCategories.set(setOf("4"))
        coEvery { categories.await(manga.id) } returns listOf(Category(4, "Protected", 0, 0))
        val current = target.copy(read = read, hidden = true)
        val currentSnapshot = listOf(current, sibling)
        coEvery { repository.getChapterByMangaId(manga.id, false) } returns currentSnapshot
        val downloaded = mockk<UniFile> { every { delete() } returns true }
        coEvery { provider.findChapterPathsForHide(any(), any(), any(), any()) } returns listOf(downloaded)

        // Deliberately stale read state must not authorize or prevent deletion.
        manager.deleteHiddenChapters(listOf(target.copy(read = !read)), manga, source)

        coVerify { downloader.dequeueAndAwait(listOf(target.copy(read = !read))) }
        coVerify(exactly = if (read) 0 else 1) {
            provider.findChapterPathsForHide(listOf(current), currentSnapshot, manga, source)
        }
        coVerify(exactly = if (read) 0 else 1) {
            provider.findMangaIdsSharingDownloadDirectory(any(), any(), any())
        }
        verify(exactly = if (read) 0 else 1) { downloaded.delete() }
        coVerify(exactly = if (read) 0 else 1) { cache.refreshMangaDirectories(any()) }
        coVerify(exactly = 0) { cache.removeChapters(any(), any()) }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `uncategorized manga uses default category zero for read protection`(read: Boolean) = runTest {
        settings.removeExcludeCategories.set(setOf("0"))
        val current = target.copy(read = read)
        coEvery { repository.getChapterByMangaId(manga.id, false) } returns listOf(current)
        val downloaded = mockk<UniFile> { every { delete() } returns true }
        coEvery { provider.findChapterPathsForHide(any(), any(), any(), any()) } returns listOf(downloaded)

        manager.deleteHiddenChapters(listOf(current), manga, source)

        coVerify { categories.await(manga.id) }
        coVerify(exactly = if (read) 0 else 1) {
            provider.findChapterPathsForHide(listOf(current), listOf(current), manga, source)
        }
        verify(exactly = if (read) 0 else 1) { downloaded.delete() }
        coVerify(exactly = if (read) 0 else 1) { cache.refreshMangaDirectories(any()) }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `bookmark eligibility uses current database flags instead of stale selection`(bookmarked: Boolean) = runTest {
        settings.removeBookmarkedChapters.set(false)
        val current = target.copy(bookmark = bookmarked, hidden = true)
        val currentSnapshot = listOf(current, sibling)
        coEvery { repository.getChapterByMangaId(manga.id, false) } returns currentSnapshot
        val downloaded = mockk<UniFile> { every { delete() } returns true }
        coEvery { provider.findChapterPathsForHide(any(), any(), any(), any()) } returns listOf(downloaded)

        manager.deleteHiddenChapters(listOf(target.copy(bookmark = !bookmarked)), manga, source)

        coVerify { downloader.dequeueAndAwait(listOf(target.copy(bookmark = !bookmarked))) }
        coVerify(exactly = if (bookmarked) 0 else 1) {
            provider.findChapterPathsForHide(listOf(current), currentSnapshot, manga, source)
        }
        verify(exactly = if (bookmarked) 0 else 1) { downloaded.delete() }
        coVerify(exactly = if (bookmarked) 0 else 1) { cache.refreshMangaDirectories(any()) }
    }

    @Test
    fun `mixed selections cancel every writer but pass only eligible current records and all owners`() = runTest {
        settings.removeBookmarkedChapters.set(false)
        val eligible = sibling.copy(hidden = true, bookmark = false, read = false, lastPageRead = 9)
        val currentTarget = target.copy(hidden = true, lastPageRead = 5)
        val currentSnapshot = listOf(currentTarget, eligible)
        coEvery { repository.getChapterByMangaId(manga.id, false) } returns currentSnapshot
        val currentManga = manga.copy(title = "Current manga title")
        coEvery { mangas.getAll() } returns listOf(currentManga)
        val downloaded = mockk<UniFile> { every { delete() } returns true }
        coEvery {
            provider.findChapterPathsForHide(listOf(eligible), currentSnapshot, currentManga, source)
        } returns listOf(downloaded)

        manager.deleteHiddenChapters(snapshot, manga, source)

        coVerifyOrder {
            downloader.dequeueAndAwait(snapshot)
            repository.getChapterByMangaId(manga.id, false)
            categories.await(currentManga.id)
            provider.findChapterPathsForHide(listOf(eligible), currentSnapshot, currentManga, source)
            downloaded.delete()
        }
        coVerify(exactly = 0) { repository.updateAll(any()) }
        coVerify(exactly = 0) { cache.removeChapters(any(), any()) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["category", "excluded categories setting", "bookmark setting"])
    fun `failed deletion policy lookup never accesses storage or clears cached downloads`(lookup: String) = runTest {
        val failure = IllegalStateException("$lookup unavailable")
        when (lookup) {
            "category" -> coEvery { categories.await(manga.id) } throws failure
            "excluded categories setting" -> every { preferences.removeExcludeCategories } throws failure
            "bookmark setting" -> every { preferences.removeBookmarkedChapters } throws failure
        }

        assertThrows<IllegalStateException> { manager.deleteHiddenChapters(listOf(target), manga, source) }

        coVerify { downloader.dequeueAndAwait(listOf(target)) }
        coVerify(exactly = 0) { provider.findMangaIdsSharingDownloadDirectory(any(), any(), any()) }
        coVerify(exactly = 0) { provider.findChapterPathsForHide(any(), any(), any(), any()) }
        coVerify(exactly = 0) { cache.refreshMangaDirectories(any()) }
        coVerify(exactly = 0) { cache.removeChapters(any(), any()) }
    }

    @Test
    fun `failed deletion is reported and cache is refreshed`() = runTest {
        val failed = mockk<UniFile> { every { delete() } returns false }
        val otherPath = mockk<UniFile> { every { delete() } returns true }
        coEvery { provider.findChapterPathsForHide(any(), any(), any(), any()) } returns listOf(failed, otherPath)

        assertThrows<IOException> { manager.deleteHiddenChapters(listOf(target), manga, source) }
        verify { otherPath.delete() }
        coVerify { cache.refreshMangaDirectories(any()) }
        coVerify(exactly = 0) { cache.removeChapters(any(), any()) }
    }

    @Test
    fun `local original files are never looked up or deleted`() = runTest {
        manager.deleteHiddenChapters(listOf(target), manga.copy(source = LocalSource.ID), source)
        coVerify { downloader.dequeueAndAwait(listOf(target)) }
        coVerify(exactly = 0) { provider.findChapterPathsForHide(any(), any(), any(), any()) }
    }

    @Test
    fun `failed ownership query never looks up files or clears the cache`() = runTest {
        coEvery { repository.getChapterByMangaId(manga.id, false) } throws IllegalStateException("database unavailable")

        assertThrows<IllegalStateException> { manager.deleteHiddenChapters(listOf(target), manga, source) }

        coVerify { downloader.dequeueAndAwait(listOf(target)) }
        coVerify(exactly = 0) { provider.findChapterPathsForHide(any(), any(), any(), any()) }
        coVerify(exactly = 0) { cache.refreshMangaDirectories(any()) }
        coVerify(exactly = 0) { cache.removeChapters(any(), any()) }
    }

    @Test
    fun `missing selected chapter in ownership snapshot prevents deletion`() = runTest {
        coEvery { repository.getChapterByMangaId(manga.id, false) } returns listOf(sibling)

        assertThrows<IOException> { manager.deleteHiddenChapters(listOf(target), manga, source) }

        coVerify(exactly = 0) { provider.findChapterPathsForHide(any(), any(), any(), any()) }
        coVerify(exactly = 0) { cache.refreshMangaDirectories(any()) }
        coVerify(exactly = 0) { cache.removeChapters(any(), any()) }
    }

    @Test
    fun `ownership and deletion use current unfiltered repository names`() = runTest {
        val current = target.copy(name = "Current title", hidden = true)
        val currentSnapshot = listOf(current, sibling)
        coEvery { repository.getChapterByMangaId(manga.id, false) } returns currentSnapshot
        coEvery { provider.findChapterPathsForHide(listOf(current), currentSnapshot, manga, source) } returns
            emptyList()

        manager.deleteHiddenChapters(listOf(target), manga, source)

        coVerify { provider.findChapterPathsForHide(listOf(current), currentSnapshot, manga, source) }
        coVerify { cache.refreshMangaDirectories(any()) }
    }

    @Test
    fun `unavailable storage is reported without clearing previously cached downloads`() = runTest {
        coEvery { provider.findChapterPathsForHide(any(), any(), any(), any()) } throws
            IOException("storage unavailable")

        assertThrows<IOException> { manager.deleteHiddenChapters(listOf(target), manga, source) }

        coVerify(exactly = 0) { cache.refreshMangaDirectories(any()) }
        coVerify(exactly = 0) { cache.removeChapters(any(), any()) }
    }

    @Test
    fun `absent downloads succeed and refresh cache from disk`() = runTest {
        coEvery { provider.findChapterPathsForHide(any(), any(), any(), any()) } returns emptyList()

        manager.deleteHiddenChapters(listOf(target), manga, source)

        coVerify { cache.refreshMangaDirectories(any()) }
        coVerify(exactly = 0) { cache.removeChapters(any(), any()) }
    }
}
