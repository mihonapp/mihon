package eu.kanade.domain.chapter.interactor

import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.source.Source
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.manga.model.Manga
import java.io.IOException

class SetChapterHiddenTest {
    private val repository = mockk<ChapterRepository>(relaxUnitFun = true)
    private val downloads = mockk<DownloadManager>(relaxUnitFun = true)
    private val source = mockk<Source>()
    private val manga = Manga.create().copy(id = 1)
    private val target = Chapter.create().copy(id = 2, mangaId = 1, bookmark = true, read = true, lastPageRead = 12)
    private val interactor = SetChapterHidden(repository, downloads)

    @Test
    fun `hide commits only visibility before cleaning the explicit targets`() = runTest {
        val updates = mutableListOf<ChapterUpdate>()
        coEvery { repository.updateAll(any()) } coAnswers { updates.addAll(firstArg()) }

        interactor.await(listOf(target), manga, source, true)

        updates.single().apply {
            id shouldBe target.id
            hidden shouldBe true
            bookmark shouldBe null
            read shouldBe null
            lastPageRead shouldBe null
        }
        coVerifyOrder {
            repository.updateAll(any())
            downloads.deleteHiddenChapters(listOf(target), manga, source)
        }
    }

    @Test
    fun `database failure never deletes downloads`() = runTest {
        coEvery { repository.updateAll(any()) } throws IllegalStateException("database failed")
        assertThrows<IllegalStateException> { interactor.await(listOf(target), manga, source, true) }
        coVerify(exactly = 0) { downloads.deleteHiddenChapters(any(), any(), any()) }
    }

    @Test
    fun `unhide changes only visibility and never downloads or deletes`() = runTest {
        interactor.await(listOf(target.copy(hidden = true)), manga, source, false)
        coVerify { repository.updateAll(match { it.single().hidden == false }) }
        coVerify(exactly = 0) { downloads.deleteHiddenChapters(any(), any(), any()) }
        coVerify(exactly = 0) { downloads.downloadChapters(any(), any(), any()) }
    }

    @Test
    fun `cleanup failure propagates and hiding again retries cleanup`() = runTest {
        val failure = IllegalStateException("storage failed")
        coEvery { downloads.deleteHiddenChapters(any(), any(), any()) } throws failure
        assertThrows<SetChapterHidden.CleanupFailed> {
            interactor.await(listOf(target), manga, source, true)
        }.cause shouldBe failure
        coEvery { downloads.deleteHiddenChapters(any(), any(), any()) } returns Unit
        interactor.await(listOf(target.copy(hidden = true)), manga, source, true)
        coVerify(exactly = 2) { downloads.deleteHiddenChapters(any(), any(), any()) }
    }

    @Test
    fun `cleanup Retry keeps only original targets still hidden and uses their current state`() = runTest {
        val unhidden = target.copy(id = 3)
        val unrelated = target.copy(id = 4, hidden = true)
        val current = target.copy(hidden = true, bookmark = false, read = false, lastPageRead = 20)
        coEvery { repository.getChapterByMangaId(manga.id, false) } returns listOf(current, unhidden, unrelated)

        interactor.retryCleanup(listOf(target, unhidden.copy(hidden = true)), manga, source)

        coVerify { downloads.deleteHiddenChapters(listOf(current), manga, source) }
        coVerify(exactly = 0) { repository.updateAll(any()) }
        coVerify(exactly = 0) { downloads.downloadChapters(any(), any(), any()) }
    }

    @Test
    fun `failed Retry snapshot never authorizes cleanup`() = runTest {
        coEvery { repository.getChapterByMangaId(manga.id, false) } throws IOException("database unavailable")

        assertThrows<IOException> { interactor.retryCleanup(listOf(target), manga, source) }

        coVerify(exactly = 0) { downloads.deleteHiddenChapters(any(), any(), any()) }
        coVerify(exactly = 0) { repository.updateAll(any()) }
    }
}
