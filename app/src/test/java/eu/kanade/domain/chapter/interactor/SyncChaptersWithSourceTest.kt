package eu.kanade.domain.chapter.interactor

import eu.kanade.domain.manga.interactor.GetExcludedScanlators
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SChapter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.interactor.ShouldUpdateDbChapter
import tachiyomi.domain.chapter.interactor.UpdateChapter
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.model.Manga

class SyncChaptersWithSourceTest {

    private lateinit var downloadManager: DownloadManager
    private lateinit var chapterRepository: ChapterRepository
    private lateinit var getChaptersByMangaId: GetChaptersByMangaId
    private lateinit var syncChaptersWithSource: SyncChaptersWithSource

    private val manga = Manga.create().copy(
        id = MANGA_ID,
        source = SOURCE_ID,
        title = "Test Manga",
        fetchInterval = 1,
        nextUpdate = Long.MAX_VALUE,
    )
    private val source = mockk<Source> {
        every { id } returns SOURCE_ID
    }
    private val presentChapter = chapter(
        id = 1,
        url = "/chapter-1",
        name = "Chapter 1",
        number = 1.0,
        sourceOrder = 0,
    )
    private val removedChapter = chapter(
        id = 2,
        url = "/chapter-2",
        name = "Chapter 2",
        number = 2.0,
        sourceOrder = 1,
    )
    private val sourceChapters = listOf(
        sourceChapter(
            url = presentChapter.url,
            name = presentChapter.name,
            number = presentChapter.chapterNumber.toFloat(),
        ),
    )

    @BeforeEach
    fun setUp() {
        downloadManager = mockk(relaxed = true)
        chapterRepository = mockk(relaxed = true)
        getChaptersByMangaId = mockk()

        val libraryPreferences = mockk<LibraryPreferences>()
        every { libraryPreferences.markDuplicateReadChapterAsRead.get() } returns emptySet()

        val getExcludedScanlators = mockk<GetExcludedScanlators>()
        coEvery { getExcludedScanlators.await(any()) } returns emptySet()

        every { downloadManager.getQueuedDownloadOrNull(any()) } returns null
        every {
            downloadManager.isChapterDownloaded(any(), any(), any(), any(), any(), any())
        } returns false
        coEvery { getChaptersByMangaId.await(MANGA_ID) } returns listOf(presentChapter, removedChapter)

        syncChaptersWithSource = SyncChaptersWithSource(
            downloadManager = downloadManager,
            downloadProvider = mockk<DownloadProvider>(relaxed = true),
            chapterRepository = chapterRepository,
            shouldUpdateDbChapter = ShouldUpdateDbChapter(),
            updateManga = mockk<UpdateManga>(relaxed = true),
            updateChapter = mockk<UpdateChapter>(relaxed = true),
            getChaptersByMangaId = getChaptersByMangaId,
            getExcludedScanlators = getExcludedScanlators,
            libraryPreferences = libraryPreferences,
        )
    }

    @Test
    fun `keeps a downloaded chapter that is missing from the source`() = runTest {
        every {
            downloadManager.isChapterDownloaded(
                removedChapter.name,
                removedChapter.scanlator,
                removedChapter.url,
                manga.title,
                manga.source,
                false,
            )
        } returns true

        syncChaptersWithSource.await(sourceChapters, manga, source)

        coVerify(exactly = 0) { chapterRepository.removeChaptersWithIds(any()) }
        verify(exactly = 0) { downloadManager.cancelQueuedDownloads(any()) }
    }

    @Test
    fun `cancels a queued chapter before removing it when it is missing from the source`() = runTest {
        val download = mockk<Download>()
        every { downloadManager.getQueuedDownloadOrNull(removedChapter.id) } returns download

        syncChaptersWithSource.await(sourceChapters, manga, source)

        coVerifyOrder {
            downloadManager.cancelQueuedDownloads(listOf(download))
            chapterRepository.removeChaptersWithIds(listOf(removedChapter.id))
        }
    }

    @Test
    fun `removes a missing chapter that is neither downloaded nor queued`() = runTest {
        syncChaptersWithSource.await(sourceChapters, manga, source)

        coVerify { chapterRepository.removeChaptersWithIds(listOf(removedChapter.id)) }
        verify(exactly = 0) { downloadManager.cancelQueuedDownloads(any()) }
    }

    @Test
    fun `keeps downloaded chapters while canceling and removing queued chapters in the same sync`() = runTest {
        val queuedChapter = chapter(
            id = 3,
            url = "/chapter-3",
            name = "Chapter 3",
            number = 3.0,
            sourceOrder = 2,
        )
        val queuedDownload = mockk<Download>()
        coEvery {
            getChaptersByMangaId.await(MANGA_ID)
        } returns listOf(presentChapter, removedChapter, queuedChapter)
        every { downloadManager.getQueuedDownloadOrNull(queuedChapter.id) } returns queuedDownload
        every {
            downloadManager.isChapterDownloaded(
                removedChapter.name,
                removedChapter.scanlator,
                removedChapter.url,
                manga.title,
                manga.source,
                false,
            )
        } returns true

        syncChaptersWithSource.await(sourceChapters, manga, source)

        coVerifyOrder {
            downloadManager.cancelQueuedDownloads(listOf(queuedDownload))
            chapterRepository.removeChaptersWithIds(listOf(queuedChapter.id))
        }
    }

    private companion object {
        const val MANGA_ID = 1L
        const val SOURCE_ID = 2L

        fun chapter(
            id: Long,
            url: String,
            name: String,
            number: Double,
            sourceOrder: Long,
        ) = Chapter.create().copy(
            id = id,
            mangaId = MANGA_ID,
            url = url,
            name = name,
            chapterNumber = number,
            dateUpload = 1_000,
            sourceOrder = sourceOrder,
        )

        fun sourceChapter(
            url: String,
            name: String,
            number: Float,
        ) = SChapter.create().apply {
            this.url = url
            this.name = name
            chapter_number = number
            date_upload = 1_000
        }
    }
}
