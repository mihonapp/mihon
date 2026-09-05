package mihon.desktop.updates

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.runBlocking
import mihon.desktop.library.db.DesktopLibraryDatabaseFactory
import mihon.desktop.library.model.ChapterRecord
import mihon.desktop.library.model.MangaRecord
import mihon.extension.source.model.SChapter
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class DesktopLibraryUpdateServiceTest {

    @Test
    fun `discovers new chapters and updates database`(@TempDir tempDir: Path) = runBlocking {
        val dbPath = tempDir.resolve("update-test.db")
        val repository = DesktopLibraryDatabaseFactory.open(dbPath)

        val mangaId = repository.insertManga(
            MangaRecord(
                sourceId = 123L,
                url = "/manga/one",
                title = "Manga One",
            ),
        )

        repository.insertChapter(
            ChapterRecord(
                mangaId = mangaId,
                url = "/ch1",
                name = "Chapter 1",
            ),
        )

        // Mock remote chapters: Chapter 1 (existing) + Chapter 2 (new!)
        val remoteChapters = listOf(
            SChapter(url = "/ch1", name = "Chapter 1"),
            SChapter(url = "/ch2", name = "Chapter 2", chapterNumber = 2.0f),
        )

        var completedResult: LibraryUpdateResult? = null

        val updateService = DesktopLibraryUpdateService(
            repository = repository,
            mutationPort = repository,
            chapterListFetcher = { sourceId, mangaUrl ->
                if (sourceId == 123L && mangaUrl == "/manga/one") remoteChapters else emptyList()
            },
            onUpdateCompleted = { completedResult = it },
        )

        val result = updateService.updateLibrary()

        result.totalMangaChecked shouldBe 1
        result.mangaWithNewChapters shouldBe 1
        result.newChaptersFound shouldBe 1
        result.updatedMangaTitles shouldBe listOf("Manga One")
        result.errors shouldBe emptyList()

        val chaptersInDb = repository.chapterSnapshot(mangaId)
        chaptersInDb shouldHaveSize 2
        chaptersInDb.any { it.url == "/ch2" && it.name == "Chapter 2" } shouldBe true

        completedResult shouldNotBe null
        repository.close()
    }

    @Test
    fun `scheduler triggerNow executes update and populates lastResult`(@TempDir tempDir: Path) = runBlocking {
        val dbPath = tempDir.resolve("sched-test.db")
        val repository = DesktopLibraryDatabaseFactory.open(dbPath)

        repository.insertManga(
            MangaRecord(
                sourceId = 456L,
                url = "/manga/two",
                title = "Manga Two",
            ),
        )

        val updateService = DesktopLibraryUpdateService(
            repository = repository,
            mutationPort = repository,
            chapterListFetcher = { _, _ -> listOf(SChapter(url = "/ch1", name = "Ch 1")) },
        )

        val scheduler = DesktopUpdateScheduler(updateService, intervalHours = 0)
        val result = scheduler.triggerNow()

        result.newChaptersFound shouldBe 1
        scheduler.lastResult.value shouldBe result

        repository.close()
    }
}
