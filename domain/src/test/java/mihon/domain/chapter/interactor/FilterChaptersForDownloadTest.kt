package mihon.domain.chapter.interactor

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.manga.model.Manga

class FilterChaptersForDownloadTest {

    private lateinit var filterChaptersForDownload: FilterChaptersForDownload
    private lateinit var getChaptersByMangaId: GetChaptersByMangaId
    private lateinit var downloadPreferences: DownloadPreferences
    private lateinit var getCategories: GetCategories

    private val testManga = Manga.create().copy(id = 1L, favorite = true)

    @BeforeEach
    fun setUp() {
        getChaptersByMangaId = mockk()
        downloadPreferences = mockk()
        getCategories = mockk()

        every { downloadPreferences.downloadNewChapters.get() } returns true
        every { downloadPreferences.downloadNewChapterCategories.get() } returns emptySet()
        every { downloadPreferences.downloadNewChapterCategoriesExclude.get() } returns emptySet()
        every { downloadPreferences.downloadNewUnreadChaptersOnly.get() } returns false
        every { downloadPreferences.autoDownloadUnreadLimit.get() } returns 0

        coEvery { getCategories.await(any()) } returns emptyList()

        filterChaptersForDownload = FilterChaptersForDownload(
            getChaptersByMangaId = getChaptersByMangaId,
            downloadPreferences = downloadPreferences,
            getCategories = getCategories,
        )
    }

    private fun createChapter(id: Long, chapterNumber: Double, read: Boolean = false, sourceOrder: Long = 0L): Chapter {
        return Chapter.create().copy(
            id = id,
            mangaId = testManga.id,
            chapterNumber = chapterNumber,
            read = read,
            sourceOrder = sourceOrder,
        )
    }

    @Test
    fun `When downloadNewChapters is disabled expect empty list`() = runTest {
        every { downloadPreferences.downloadNewChapters.get() } returns false

        val newChapters = listOf(createChapter(id = 101, chapterNumber = 1.0))
        val result = filterChaptersForDownload.await(testManga, newChapters)

        result shouldBe emptyList()
    }

    @Test
    fun `When limit is 0 (disabled) expect all new chapters returned`() = runTest {
        every { downloadPreferences.autoDownloadUnreadLimit.get() } returns 0
        coEvery { getChaptersByMangaId.await(testManga.id) } returns listOf(
            createChapter(id = 1, chapterNumber = 1.0, read = true),
            createChapter(id = 2, chapterNumber = 2.0, read = false),
        )

        val newChapters = listOf(
            createChapter(id = 3, chapterNumber = 3.0),
            createChapter(id = 4, chapterNumber = 4.0),
        )

        val result = filterChaptersForDownload.await(testManga, newChapters)

        result shouldBe newChapters
    }

    @Test
    fun `When limit is active and series has 0 read chapters expect empty list`() = runTest {
        every { downloadPreferences.autoDownloadUnreadLimit.get() } returns 5

        // Manga has existing chapters but NONE are read
        val existing = listOf(
            createChapter(id = 1, chapterNumber = 1.0, read = false),
            createChapter(id = 2, chapterNumber = 2.0, read = false),
        )
        val newChapters = listOf(
            createChapter(id = 3, chapterNumber = 3.0, read = false),
        )

        coEvery { getChaptersByMangaId.await(testManga.id) } returns (existing + newChapters)

        val result = filterChaptersForDownload.await(testManga, newChapters)

        result shouldBe emptyList()
    }

    @Test
    fun `When limit is active and X is less than A expect empty list`() = runTest {
        every { downloadPreferences.autoDownloadUnreadLimit.get() } returns 5

        // Read up to chapter 10, but has 10 unread chapters already (11..20). X=5 < A=10
        val existing = listOf(createChapter(id = 10, chapterNumber = 10.0, read = true)) +
            (11..20).map { createChapter(id = it.toLong(), chapterNumber = it.toDouble(), read = false) }
        val newChapters = listOf(
            createChapter(id = 21, chapterNumber = 21.0),
        )

        coEvery { getChaptersByMangaId.await(testManga.id) } returns (existing + newChapters)

        val result = filterChaptersForDownload.await(testManga, newChapters)

        result shouldBe emptyList()
    }

    @Test
    fun `When limit is active and X equals A expect empty list`() = runTest {
        every { downloadPreferences.autoDownloadUnreadLimit.get() } returns 5

        // Read up to chapter 10, but has 5 unread chapters already (11..15). X=5 == A=5
        val existing = listOf(createChapter(id = 10, chapterNumber = 10.0, read = true)) +
            (11..15).map { createChapter(id = it.toLong(), chapterNumber = it.toDouble(), read = false) }
        val newChapters = listOf(
            createChapter(id = 16, chapterNumber = 16.0),
        )

        coEvery { getChaptersByMangaId.await(testManga.id) } returns (existing + newChapters)

        val result = filterChaptersForDownload.await(testManga, newChapters)

        result shouldBe emptyList()
    }

    @Test
    fun `When X is greater than A with A not downloaded expect A and at most X minus A from B`() = runTest {
        every { downloadPreferences.autoDownloadUnreadLimit.get() } returns 5

        // Read up to chapter 10, unread chapters 11 and 12 (A=2, not downloaded).
        val ch11 = createChapter(id = 11, chapterNumber = 11.0, read = false)
        val ch12 = createChapter(id = 12, chapterNumber = 12.0, read = false)
        val existing = listOf(
            createChapter(id = 10, chapterNumber = 10.0, read = true),
            ch11,
            ch12,
        )

        // 6 new chapters arrive in descending order (B)
        val ch13 = createChapter(id = 13, chapterNumber = 13.0, sourceOrder = 6)
        val ch14 = createChapter(id = 14, chapterNumber = 14.0, sourceOrder = 5)
        val ch15 = createChapter(id = 15, chapterNumber = 15.0, sourceOrder = 4)
        val ch16 = createChapter(id = 16, chapterNumber = 16.0, sourceOrder = 3)
        val ch17 = createChapter(id = 17, chapterNumber = 17.0, sourceOrder = 2)
        val ch18 = createChapter(id = 18, chapterNumber = 18.0, sourceOrder = 1)

        val newChapters = listOf(ch18, ch17, ch16, ch15, ch14, ch13)

        coEvery { getChaptersByMangaId.await(testManga.id) } returns (existing + newChapters)

        val result = filterChaptersForDownload.await(
            manga = testManga,
            newChapters = newChapters,
            isChapterDownloaded = { false }, // A is not downloaded
        )

        // Downloads A (11, 12) + at most (5 - 2 = 3) from B (13, 14, 15) in reading order
        result.shouldContainExactly(ch11, ch12, ch13, ch14, ch15)
    }

    @Test
    fun `When X is greater than A with A already downloaded expect only X minus A from B`() = runTest {
        every { downloadPreferences.autoDownloadUnreadLimit.get() } returns 5

        // Read up to chapter 10, unread chapters 11 and 12 (A=2, already downloaded).
        val ch11 = createChapter(id = 11, chapterNumber = 11.0, read = false)
        val ch12 = createChapter(id = 12, chapterNumber = 12.0, read = false)
        val existing = listOf(
            createChapter(id = 10, chapterNumber = 10.0, read = true),
            ch11,
            ch12,
        )

        // 6 new chapters arrive in descending order (B)
        val ch13 = createChapter(id = 13, chapterNumber = 13.0, sourceOrder = 6)
        val ch14 = createChapter(id = 14, chapterNumber = 14.0, sourceOrder = 5)
        val ch15 = createChapter(id = 15, chapterNumber = 15.0, sourceOrder = 4)
        val ch16 = createChapter(id = 16, chapterNumber = 16.0, sourceOrder = 3)
        val ch17 = createChapter(id = 17, chapterNumber = 17.0, sourceOrder = 2)
        val ch18 = createChapter(id = 18, chapterNumber = 18.0, sourceOrder = 1)

        val newChapters = listOf(ch18, ch17, ch16, ch15, ch14, ch13)

        coEvery { getChaptersByMangaId.await(testManga.id) } returns (existing + newChapters)

        val result = filterChaptersForDownload.await(
            manga = testManga,
            newChapters = newChapters,
            isChapterDownloaded = { it.id in setOf(11L, 12L) }, // A is already downloaded
        )

        // Only at most (5 - 2 = 3) from B (13, 14, 15) in reading order
        result.shouldContainExactly(ch13, ch14, ch15)
    }

    @Test
    fun `When X is greater than A with partially downloaded A expect remaining A and from B`() = runTest {
        every { downloadPreferences.autoDownloadUnreadLimit.get() } returns 5

        // Read up to chapter 10, unread chapters 11 and 12. Chapter 11 is downloaded, 12 is NOT.
        val ch11 = createChapter(id = 11, chapterNumber = 11.0, read = false)
        val ch12 = createChapter(id = 12, chapterNumber = 12.0, read = false)
        val existing = listOf(
            createChapter(id = 10, chapterNumber = 10.0, read = true),
            ch11,
            ch12,
        )

        val ch13 = createChapter(id = 13, chapterNumber = 13.0, sourceOrder = 2)
        val ch14 = createChapter(id = 14, chapterNumber = 14.0, sourceOrder = 1)

        val newChapters = listOf(ch14, ch13)

        coEvery { getChaptersByMangaId.await(testManga.id) } returns (existing + newChapters)

        val result = filterChaptersForDownload.await(
            manga = testManga,
            newChapters = newChapters,
            isChapterDownloaded = { it.id == 11L }, // Only chapter 11 is downloaded
        )

        // Remaining A (12) + at most (5 - 2 = 3) from B (13, 14)
        result.shouldContainExactly(ch12, ch13, ch14)
    }

    @Test
    fun `When user skipped filler in middle expect filler ignored and contiguous unread considered`() = runTest {
        every { downloadPreferences.autoDownloadUnreadLimit.get() } returns 5

        // Read up to 10, chapter 11 unread (skipped filler), 12..20 read, 21 and 22 unread (latest contiguous block of A=2)
        val ch11Filler = createChapter(id = 11, chapterNumber = 11.0, read = false)
        val ch21 = createChapter(id = 21, chapterNumber = 21.0, read = false)
        val ch22 = createChapter(id = 22, chapterNumber = 22.0, read = false)

        val existing = (1..10).map { createChapter(id = it.toLong(), chapterNumber = it.toDouble(), read = true) } +
            listOf(ch11Filler) +
            (12..20).map { createChapter(id = it.toLong(), chapterNumber = it.toDouble(), read = true) } +
            listOf(ch21, ch22)

        val ch23 = createChapter(id = 23, chapterNumber = 23.0, sourceOrder = 3)
        val ch24 = createChapter(id = 24, chapterNumber = 24.0, sourceOrder = 2)
        val ch25 = createChapter(id = 25, chapterNumber = 25.0, sourceOrder = 1)
        val newChapters = listOf(ch25, ch24, ch23)

        coEvery { getChaptersByMangaId.await(testManga.id) } returns (existing + newChapters)

        val result = filterChaptersForDownload.await(
            manga = testManga,
            newChapters = newChapters,
            isChapterDownloaded = { false },
        )

        // Filler ch11 is ignored. Contiguous A (21, 22) + at most (5 - 2 = 3) from B (23, 24, 25)
        result.shouldContainExactly(ch21, ch22, ch23, ch24, ch25)
    }

    @Test
    fun `When user skipped filler and caught up at latest expect filler ignored and new downloaded`() = runTest {
        every { downloadPreferences.autoDownloadUnreadLimit.get() } returns 5

        // Read up to 10, chapter 11 unread (skipped filler), 12..20 read (latest existing is 20 which is read -> A=0)
        val ch11Filler = createChapter(id = 11, chapterNumber = 11.0, read = false)
        val existing = (1..10).map { createChapter(id = it.toLong(), chapterNumber = it.toDouble(), read = true) } +
            listOf(ch11Filler) +
            (12..20).map { createChapter(id = it.toLong(), chapterNumber = it.toDouble(), read = true) }

        val ch21 = createChapter(id = 21, chapterNumber = 21.0, sourceOrder = 3)
        val ch22 = createChapter(id = 22, chapterNumber = 22.0, sourceOrder = 2)
        val ch23 = createChapter(id = 23, chapterNumber = 23.0, sourceOrder = 1)
        val newChapters = listOf(ch23, ch22, ch21)

        coEvery { getChaptersByMangaId.await(testManga.id) } returns (existing + newChapters)

        val result = filterChaptersForDownload.await(
            manga = testManga,
            newChapters = newChapters,
            isChapterDownloaded = { false },
        )

        // A is 0 because the latest chapter 20 is read. Downloads 3 chapters from B up to limit 5.
        result.shouldContainExactly(ch21, ch22, ch23)
    }

    @Test
    fun `When limit is active and user is caught up expect new chapters up to limit`() = runTest {
        every { downloadPreferences.autoDownloadUnreadLimit.get() } returns 2

        // Read up to chapter 10, 0 existing unread chapters.
        val existing = listOf(
            createChapter(id = 10, chapterNumber = 10.0, read = true),
        )

        val ch11 = createChapter(id = 11, chapterNumber = 11.0, sourceOrder = 3)
        val ch12 = createChapter(id = 12, chapterNumber = 12.0, sourceOrder = 2)
        val ch13 = createChapter(id = 13, chapterNumber = 13.0, sourceOrder = 1)

        val newChapters = listOf(ch13, ch12, ch11)

        coEvery { getChaptersByMangaId.await(testManga.id) } returns (existing + newChapters)

        val result = filterChaptersForDownload.await(testManga, newChapters)

        result.shouldContainExactly(ch11, ch12)
    }

    @Test
    fun `When multiple groups upload same chapter number expect deduplicated to single slot`() = runTest {
        every { downloadPreferences.autoDownloadUnreadLimit.get() } returns 3

        val existing = listOf(
            createChapter(id = 1, chapterNumber = 1.0, read = true),
        )

        // Two groups upload chapter 2.0, then chapter 3.0 and 4.0
        val ch2GroupA = createChapter(id = 21, chapterNumber = 2.0, sourceOrder = 4)
        val ch2GroupB = createChapter(id = 22, chapterNumber = 2.0, sourceOrder = 3)
        val ch3 = createChapter(id = 30, chapterNumber = 3.0, sourceOrder = 2)
        val ch4 = createChapter(id = 40, chapterNumber = 4.0, sourceOrder = 1)

        val newChapters = listOf(ch2GroupA, ch2GroupB, ch3, ch4)

        coEvery { getChaptersByMangaId.await(testManga.id) } returns (existing + newChapters)

        val result = filterChaptersForDownload.await(testManga, newChapters)

        // Allowance is 3. Only 1 version of chapter 2 should be taken, leaving slots for ch3 and ch4.
        result.shouldContainExactly(ch2GroupA, ch3, ch4)
    }

    @Test
    fun `When downloadNewUnreadChaptersOnly is enabled expect duplicate read chapters filtered out`() = runTest {
        every { downloadPreferences.downloadNewUnreadChaptersOnly.get() } returns true
        every { downloadPreferences.autoDownloadUnreadLimit.get() } returns 5

        // Chapter 5 was already read
        val existing = listOf(
            createChapter(id = 5, chapterNumber = 5.0, read = true),
        )

        // New chapter 5.0 (re-upload) and 6.0 arrive
        val ch5Reupload = createChapter(id = 55, chapterNumber = 5.0, sourceOrder = 2)
        val ch6 = createChapter(id = 60, chapterNumber = 6.0, sourceOrder = 1)

        val newChapters = listOf(ch5Reupload, ch6)

        coEvery { getChaptersByMangaId.await(testManga.id) } returns (existing + newChapters)

        val result = filterChaptersForDownload.await(testManga, newChapters)

        result.shouldContainExactly(ch6)
    }
}
