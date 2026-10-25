package eu.kanade.tachiyomi.data.download

import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.source.Source
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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

    // No exclusions may be queried on the hide path, including bookmarks and category protections.
    private val preferences = mockk<DownloadPreferences>()
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
        mockk(), provider, cache, mockk(), mockk(), mockk(), repository, mockk(), preferences, downloader,
        mockk(), mangas,
    )

    @Test
    fun `hide deletes protected target final and partial paths only after writers finish`() = runTest {
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
            cache.invalidateCache()
        }
        verify(exactly = 0) { unrelated.delete() }
        coVerify(exactly = 0) { cache.removeChapters(any(), any()) }
    }

    @Test
    fun `failed deletion is reported and cache is refreshed`() = runTest {
        val failed = mockk<UniFile> { every { delete() } returns false }
        val otherPath = mockk<UniFile> { every { delete() } returns true }
        coEvery { provider.findChapterPathsForHide(any(), any(), any(), any()) } returns listOf(failed, otherPath)

        assertThrows<IOException> { manager.deleteHiddenChapters(listOf(target), manga, source) }
        verify { otherPath.delete() }
        verify { cache.invalidateCache() }
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
        verify(exactly = 0) { cache.invalidateCache() }
        coVerify(exactly = 0) { cache.removeChapters(any(), any()) }
    }

    @Test
    fun `missing selected chapter in ownership snapshot prevents deletion`() = runTest {
        coEvery { repository.getChapterByMangaId(manga.id, false) } returns listOf(sibling)

        assertThrows<IOException> { manager.deleteHiddenChapters(listOf(target), manga, source) }

        coVerify(exactly = 0) { provider.findChapterPathsForHide(any(), any(), any(), any()) }
        verify(exactly = 0) { cache.invalidateCache() }
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
        verify { cache.invalidateCache() }
    }

    @Test
    fun `unavailable storage is reported without clearing previously cached downloads`() = runTest {
        coEvery { provider.findChapterPathsForHide(any(), any(), any(), any()) } throws
            IOException("storage unavailable")

        assertThrows<IOException> { manager.deleteHiddenChapters(listOf(target), manga, source) }

        verify(exactly = 0) { cache.invalidateCache() }
        coVerify(exactly = 0) { cache.removeChapters(any(), any()) }
    }

    @Test
    fun `absent downloads succeed and refresh cache from disk`() = runTest {
        coEvery { provider.findChapterPathsForHide(any(), any(), any(), any()) } returns emptyList()

        manager.deleteHiddenChapters(listOf(target), manga, source)

        verify { cache.invalidateCache() }
        coVerify(exactly = 0) { cache.removeChapters(any(), any()) }
    }
}
