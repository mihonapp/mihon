package mihon.desktop.stats

import kotlinx.coroutines.runBlocking
import mihon.desktop.library.db.DesktopLibraryDatabaseFactory
import mihon.desktop.library.model.CategoryRecord
import mihon.desktop.library.model.ChapterRecord
import mihon.desktop.library.model.HistoryRecord
import mihon.desktop.library.model.MangaRecord
import mihon.desktop.library.model.TrackingRecord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class DesktopStatsServiceTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `computeStats accurately aggregates library, chapters, history, and tracking`() = runBlocking {
        val dbFile = tempDir.resolve("stats-test.db")
        val repo = DesktopLibraryDatabaseFactory.open(dbFile)

        try {
            // 1. Insert Manga
            val manga1Id = repo.insertManga(
                MangaRecord(
                    sourceId = 1L,
                    url = "/manga/1",
                    title = "Action Manga",
                    genreJson = """["Action", "Adventure"]""",
                    status = 1L, // Ongoing
                    favorite = true,
                ),
            )
            val manga2Id = repo.insertManga(
                MangaRecord(
                    sourceId = 1L,
                    url = "/manga/2",
                    title = "Romance Manga",
                    genreJson = """["Romance", "Action"]""",
                    status = 2L, // Completed
                    favorite = true,
                ),
            )

            // 2. Insert Chapters
            val m1c1 = repo.insertChapter(
                ChapterRecord(mangaId = manga1Id, url = "/c1", name = "Ch 1", read = true),
            )
            repo.insertChapter(
                ChapterRecord(mangaId = manga1Id, url = "/c2", name = "Ch 2", read = false),
            )
            val m2c1 = repo.insertChapter(
                ChapterRecord(mangaId = manga2Id, url = "/c1", name = "Ch 1", read = true),
            )
            repo.insertChapter(
                ChapterRecord(mangaId = manga2Id, url = "/c2", name = "Ch 2", read = true),
            )

            // 3. Insert History
            repo.upsertHistory(HistoryRecord(chapterId = m1c1, lastRead = 1000L, readDuration = 120_000L))
            repo.upsertHistory(HistoryRecord(chapterId = m2c1, lastRead = 2000L, readDuration = 240_000L))

            // 4. Insert Category & Link
            val catId = repo.upsertCategory(CategoryRecord(name = "Favorites", sortOrder = 1L))
            repo.linkCategory(manga1Id, catId)

            // 5. Insert Tracking
            repo.insertTracking(
                TrackingRecord(
                    mangaId = manga1Id,
                    trackerId = 1L,
                    remoteId = 101L,
                    title = "Action Manga",
                    score = 8.0,
                ),
            )
            repo.insertTracking(
                TrackingRecord(
                    mangaId = manga2Id,
                    trackerId = 2L,
                    remoteId = 202L,
                    title = "Romance Manga",
                    score = 9.0,
                ),
            )

            val service = DesktopStatsService(repo)
            val stats = service.computeStats()

            // Overview assertions
            assertEquals(2, stats.overview.libraryMangaCount)
            assertEquals(1, stats.overview.completedMangaCount) // manga 2 is completed status and all chapters read
            assertEquals(360_000L, stats.overview.totalReadDurationMillis)
            assertEquals("6m", stats.overview.formattedReadDuration)
            assertEquals(4, stats.overview.totalChapterCount)
            assertEquals(3, stats.overview.readChapterCount)
            assertEquals(1, stats.overview.unreadChapterCount)
            assertEquals(75.0f, stats.overview.readPercentage)

            // Status distribution assertions
            assertEquals(1, stats.statuses.ongoingCount)
            assertEquals(1, stats.statuses.completedCount)
            assertEquals(0, stats.statuses.cancelledCount)

            // Progress distribution assertions
            assertEquals(0, stats.progress.unreadCount)
            assertEquals(1, stats.progress.inProgressCount)
            assertEquals(1, stats.progress.finishedCount)

            // Top genres assertions
            assertEquals("Action", stats.topGenres[0].genre)
            assertEquals(2, stats.topGenres[0].count)
            assertEquals(100.0f, stats.topGenres[0].percentage)

            // Categories assertions
            assertEquals(1, stats.categories.size)
            assertEquals("Favorites", stats.categories[0].categoryName)
            assertEquals(1, stats.categories[0].mangaCount)

            // Tracking assertions
            assertEquals(2, stats.tracking.trackedMangaCount)
            assertEquals(8.5, stats.tracking.meanScore)
            assertEquals(2, stats.tracking.trackerCount)
        } finally {
            repo.close()
        }
    }
}
