package eu.kanade.tachiyomi.data.download

import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.source.Source
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.source.local.LocalSource
import java.io.IOException

class DownloadSharedDirectoryTest {
    @ParameterizedTest
    @CsvSource(
        "Manga,Manga,7,Source/Name",
        "Manga/Title,Manga:Title,7,Source/Name",
        "Manga/Title,Manga:Title,8,Source:Name",
        "Manga/Title,Manga:Title,8,Different source alias",
        "Manga,manga,7,Source/Name",
    )
    fun `hiding preserves other manga downloads sharing sanitized names or actual storage identity`(
        title: String,
        otherTitle: String,
        otherSourceId: Long,
        otherSourceName: String,
    ) = runTest {
        val fixture = SharedStorage(title, otherTitle, otherSourceId, otherSourceName)

        fixture.hide()

        verify(exactly = 0) { fixture.shared.delete() }
        verify(exactly = 0) { fixture.partial.delete() }
        verify(exactly = 0) { fixture.archive.delete() }
        verify(exactly = 0) { fixture.unrelatedFile.delete() }
        verify(exactly = 1) { fixture.owned.delete() }
        verify(exactly = 1) { fixture.cache.invalidateCache() }
    }

    @ParameterizedTest
    @ValueSource(strings = ["manga", "chapter", "source", "storage"])
    fun `ownership lookup failures prevent every deletion and preserve cached state`(failedLookup: String) = runTest {
        val fixture = SharedStorage("Manga/Title", "Manga:Title", 8, "Source:Name")
        val failure = IOException("$failedLookup ownership unavailable")
        when (failedLookup) {
            "manga" -> coEvery { fixture.mangas.getAll() } throws failure
            "chapter" -> coEvery { fixture.chapters.getChapterByMangaId(2, false) } throws failure
            "source" -> coEvery { fixture.sources.getOrStub(8) } throws failure
            "storage" -> every { fixture.sourceDir.canRead() } returns false
        }

        val thrown = assertThrows<IOException> { fixture.hide() }
        if (failedLookup != "storage") thrown shouldBe failure
        verify(exactly = 0) { fixture.shared.delete() }
        verify(exactly = 0) { fixture.partial.delete() }
        verify(exactly = 0) { fixture.owned.delete() }
        verify(exactly = 0) { fixture.cache.invalidateCache() }
    }

    private class SharedStorage(title: String, otherTitle: String, otherSourceId: Long, otherSourceName: String) {
        private val source = mockk<Source> {
            every { id } returns 7
        }
        private val otherSource = if (otherSourceId == 7L) {
            source
        } else {
            mockk<Source> {
                every { id } returns otherSourceId
            }
        }
        private val unrelatedSource = mockk<Source> {
            every { id } returns 9
        }
        private val manga = Manga.create().copy(id = 1, source = 7, title = title)
        private val otherManga = manga.copy(id = 2, source = otherSourceId, title = otherTitle, url = "/other")
        private val unrelatedManga = manga.copy(id = 3, source = 9)
        private val localManga = manga.copy(id = 4, source = LocalSource.ID)
        private val target = Chapter.create().copy(
            id = 10,
            mangaId = 1,
            name = "Chapter 1",
            url = "/a/1",
            hidden = true,
        )
        private val otherChapter = target.copy(id = 20, mangaId = 2, url = "/b/1", hidden = false)
        private val root = directory()
        val sourceDir = directory()
        private val mangaDir = directory()
        private val unrelatedSourceDir = directory()
        private val unrelatedMangaDir = directory()
        private val storage = mockk<StorageManager> { every { getDownloadsDirectory() } returns root }
        private val provider = DownloadProvider(
            mockk(),
            storage,
            LibraryPreferences(InMemoryPreferenceStore()).apply { enableChapterNameHash.set(true) },
        )
        val shared = file()
        val partial = file()
        val archive = file()
        val owned = file()
        val unrelatedFile = file()
        val cache = mockk<DownloadCache>(relaxUnitFun = true)
        val chapters = mockk<ChapterRepository> {
            coEvery { getChapterByMangaId(1, false) } returns listOf(target)
            coEvery { getChapterByMangaId(2, false) } returns listOf(otherChapter)
            coEvery { getChapterByMangaId(3, false) } throws
                AssertionError("Unrelated manga must not protect target paths")
        }
        val mangas = mockk<MangaRepository> {
            coEvery { getAll() } returns listOf(manga, otherManga, unrelatedManga, localManga)
        }
        val sources = mockk<SourceManager> {
            coEvery { getOrStub(otherSourceId) } returns otherSource
            coEvery { getOrStub(9) } returns unrelatedSource
        }
        private val manager = DownloadManager(
            mockk(), provider, cache, mockk(), mockk(), mockk(), chapters,
            sources, mockk(), mockk(relaxUnitFun = true), mockk(), mangas,
        )

        init {
            every { source.toString() } returns "Source/Name"
            every { otherSource.toString() } returns otherSourceName
            every { unrelatedSource.toString() } returns "Unrelated"
            every { root.findFile(any()) } answers {
                if (firstArg<String>() == "Unrelated") unrelatedSourceDir else sourceDir
            }
            every { sourceDir.findFile(any()) } returns mangaDir
            every { unrelatedSourceDir.findFile(any()) } returns unrelatedMangaDir
            every { unrelatedMangaDir.findFile(any()) } returns unrelatedFile
            val ownedName = provider.getChapterDirName(target.name, target.scanlator, target.url)
            every { mangaDir.findFile(any()) } answers {
                when (firstArg<String>()) {
                    "Chapter 1" -> shared
                    "Chapter 1_tmp" -> partial
                    "Chapter 1.cbz" -> archive
                    ownedName -> owned
                    else -> null
                }
            }
        }

        suspend fun hide() = manager.deleteHiddenChapters(listOf(target), manga, source)

        private fun directory() = mockk<UniFile> {
            every { exists() } returns true
            every { isDirectory } returns true
            every { canRead() } returns true
            every { uri } returns mockk(relaxed = true)
        }

        private fun file() = mockk<UniFile> {
            every { uri } returns mockk()
            every { delete() } returns true
        }
    }
}
