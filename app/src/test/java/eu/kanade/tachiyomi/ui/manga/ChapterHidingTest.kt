package eu.kanade.tachiyomi.ui.manga

import androidx.compose.material3.SnackbarHostState
import eu.kanade.domain.chapter.interactor.SetChapterHidden
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.source.Source
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.manga.model.Manga
import java.io.IOException

class ChapterHidingTest {
    private val repository = mockk<ChapterRepository>()
    private val downloads = mockk<DownloadManager>(relaxUnitFun = true)
    private val interactor = SetChapterHidden(repository, downloads)
    private val manga = Manga.create().copy(id = 1)
    private val source = mockk<Source>()
    private val target = Chapter.create().copy(id = 2, mangaId = 1, read = true, bookmark = true, lastPageRead = 12)
    private var current = target
    private val updates = mutableListOf<ChapterUpdate>()

    init {
        coEvery { repository.updateAll(any()) } coAnswers {
            val changes = firstArg<List<ChapterUpdate>>()
            updates.addAll(changes)
            current = current.copy(hidden = changes.single().hidden!!)
        }
        coEvery { repository.getChapterByMangaId(1, false) } answers { listOf(current) }
    }

    @Test
    fun `failed hide offers Retry for original targets even after all chapters become hidden`() = runTest {
        val snackbar = SnackbarHostState()
        val targets = mutableListOf(target)
        coEvery { downloads.deleteHiddenChapters(any(), any(), any()) } throws IOException("storage offline")
        val action = launch {
            snackbar.setChapterHiddenWithRetry(interactor, targets, manga, source, true, "Hide failed", "Retry")
        }
        runCurrent()
        current.hidden shouldBe true
        snackbar.currentSnackbarData!!.visuals.actionLabel shouldBe "Retry"

        // Selection/UI snapshots are gone by the time the user presses Retry.
        targets.clear()
        coEvery { downloads.deleteHiddenChapters(any(), any(), any()) } returns Unit
        snackbar.currentSnackbarData!!.performAction()
        runCurrent()

        action.isCompleted shouldBe true
        updates.size shouldBe 1
        current shouldBe target.copy(hidden = true)
        coVerify { downloads.deleteHiddenChapters(listOf(current), manga, source) }
        coVerify(exactly = 0) { downloads.downloadChapters(any(), any(), any()) }
    }

    @Test
    fun `Retry after a subsequent Unhide neither hides again nor deletes downloads`() = runTest {
        val snackbar = SnackbarHostState()
        coEvery { downloads.deleteHiddenChapters(any(), any(), any()) } throws IOException("storage offline")
        val action = launch {
            snackbar.setChapterHiddenWithRetry(interactor, listOf(target), manga, source, true, "Hide failed", "Retry")
        }
        runCurrent()
        interactor.await(listOf(current), manga, source, false)
        // Reader/bookmark changes while the snackbar is pending must survive as well.
        current = current.copy(read = false, bookmark = false, lastPageRead = 18)
        val afterUnhide = current

        snackbar.currentSnackbarData!!.performAction()
        runCurrent()

        action.isCompleted shouldBe true
        current shouldBe afterUnhide
        updates.map { it.hidden } shouldBe listOf(true, false)
        coVerify(exactly = 1) { downloads.deleteHiddenChapters(any(), any(), any()) }
        coVerify(exactly = 0) { downloads.downloadChapters(any(), any(), any()) }
    }

    @Test
    fun `failed Retry offers another explicit attempt and dismissing stops cleanup`() = runTest {
        val snackbar = SnackbarHostState()
        coEvery { downloads.deleteHiddenChapters(any(), any(), any()) } throws IOException("storage offline")
        val action = launch {
            snackbar.setChapterHiddenWithRetry(interactor, listOf(target), manga, source, true, "Hide failed", "Retry")
        }
        runCurrent()
        snackbar.currentSnackbarData!!.performAction()
        runCurrent()
        snackbar.currentSnackbarData!!.visuals.actionLabel shouldBe "Retry"
        snackbar.currentSnackbarData!!.dismiss()
        runCurrent()

        action.isCompleted shouldBe true
        updates.size shouldBe 1
        coVerify(exactly = 2) { downloads.deleteHiddenChapters(any(), any(), any()) }
    }

    @Test
    fun `failed Unhide does not offer download cleanup Retry`() = runTest {
        val snackbar = SnackbarHostState()
        coEvery { repository.updateAll(any()) } throws IOException("database unavailable")
        val action = launch {
            snackbar.setChapterHiddenWithRetry(interactor, listOf(target), manga, source, false, "Hide failed", "Retry")
        }
        runCurrent()
        snackbar.currentSnackbarData!!.visuals.actionLabel shouldBe null
        snackbar.currentSnackbarData!!.dismiss()
        runCurrent()

        action.isCompleted shouldBe true
        coVerify(exactly = 0) { downloads.deleteHiddenChapters(any(), any(), any()) }
    }

    @Test
    fun `failed initial visibility write does not offer misleading cleanup Retry`() = runTest {
        val snackbar = SnackbarHostState()
        coEvery { repository.updateAll(any()) } throws IOException("database unavailable")
        val action = launch {
            snackbar.setChapterHiddenWithRetry(interactor, listOf(target), manga, source, true, "Hide failed", "Retry")
        }
        runCurrent()

        current shouldBe target
        snackbar.currentSnackbarData!!.visuals.actionLabel shouldBe null
        snackbar.currentSnackbarData!!.dismiss()
        runCurrent()

        action.isCompleted shouldBe true
        coVerify(exactly = 0) { downloads.deleteHiddenChapters(any(), any(), any()) }
        coVerify(exactly = 0) { repository.getChapterByMangaId(any(), any()) }
    }
}
