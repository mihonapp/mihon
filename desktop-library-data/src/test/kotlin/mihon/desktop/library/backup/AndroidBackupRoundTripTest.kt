package mihon.desktop.library.backup

import io.kotest.matchers.shouldBe
import kotlinx.serialization.ExperimentalSerializationApi
import mihon.desktop.library.db.DesktopLibraryDatabaseFactory
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

@OptIn(ExperimentalSerializationApi::class)
class AndroidBackupRoundTripTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `import backup into database then export produces semantically equivalent backup`() {
        val originalBackup = AndroidBackup(
            backupManga = listOf(
                AndroidBackupManga(
                    source = 100L,
                    url = "/manga/roundtrip",
                    title = "Roundtrip Manga",
                    artist = "Artist Name",
                    author = "Author Name",
                    description = "Description here",
                    genre = listOf("Action", "Adventure"),
                    status = 1,
                    thumbnailUrl = "https://example.com/cover.jpg",
                    dateAdded = 123456789L,
                    chapters = listOf(
                        AndroidBackupChapter(
                            url = "/chapter/1",
                            name = "Chapter 1",
                            scanlator = "Scan Group",
                            read = true,
                            bookmark = false,
                            lastPageRead = 15L,
                            dateFetch = 1000L,
                            dateUpload = 2000L,
                            chapterNumber = 1.0f,
                            sourceOrder = 1L,
                            lastModifiedAt = 3000L,
                            version = 1L,
                        ),
                    ),
                    categories = listOf(1L),
                    tracking = listOf(
                        AndroidBackupTracking(
                            syncId = 1,
                            libraryId = 10L,
                            mediaId = 999L,
                            trackingUrl = "https://myanimelist.net/manga/999",
                            title = "Roundtrip Manga",
                            lastChapterRead = 1.0f,
                            totalChapters = 50,
                            score = 8.5f,
                            status = 2,
                            startedReadingDate = 1000L,
                            finishedReadingDate = 2000L,
                            private = false,
                        ),
                    ),
                    favorite = true,
                    history = listOf(
                        AndroidBackupHistory(
                            url = "/chapter/1",
                            lastRead = 5000L,
                            readDuration = 300L,
                        ),
                    ),
                    updateStrategy = AndroidUpdateStrategy.ALWAYS_UPDATE,
                    lastModifiedAt = 4000L,
                    version = 2L,
                    notes = "My notes",
                    initialized = true,
                ),
            ),
            backupCategories = listOf(
                AndroidBackupCategory(
                    name = "Favorites",
                    order = 1L,
                    id = 1L,
                    flags = 0L,
                ),
            ),
            backupSources = listOf(
                AndroidBackupSource(
                    name = "Test Source",
                    sourceId = 100L,
                ),
            ),
            backupPreferences = listOf(
                AndroidBackupPreference(
                    key = "pref_display_mode_library",
                    value = AndroidStringPreferenceValue("COMPACT_GRID"),
                ),
            ),
            backupSourcePreferences = listOf(
                AndroidBackupSourcePreferences(
                    sourceKey = "test_src",
                    prefs = listOf(
                        AndroidBackupPreference(
                            key = "quality",
                            value = AndroidStringPreferenceValue("HIGH"),
                        ),
                    ),
                ),
            ),
        )

        val inputBackupFile = tempDir.resolve("input.tachibk")
        val codec = AndroidBackupCodec()
        codec.encode(originalBackup, inputBackupFile)

        val dbFile = tempDir.resolve("roundtrip.db")
        DesktopLibraryDatabaseFactory.open(dbFile).use { repository ->
            val importer = AndroidBackupImporter(
                codec = codec,
                validator = AndroidBackupValidator(),
                mutations = repository,
                preferences = SupportedPreferencePolicy(
                    appKeys = setOf("pref_display_mode_library"),
                    sourceKeys = mapOf("test_src" to setOf("quality")),
                ),
            )
            val report = importer.import(inputBackupFile, 10000L)
            report.counts.mangaInserted shouldBe 1L
            report.counts.chaptersInserted shouldBe 1L
            report.counts.categoriesLinked shouldBe 1L

            val exporter = AndroidBackupExporter(repository, codec)
            val exportedBackup = exporter.createBackup()

            exportedBackup.backupManga.size shouldBe 1
            val exportedManga = exportedBackup.backupManga.first()
            exportedManga.title shouldBe "Roundtrip Manga"
            exportedManga.url shouldBe "/manga/roundtrip"
            exportedManga.source shouldBe 100L
            exportedManga.artist shouldBe "Artist Name"
            exportedManga.author shouldBe "Author Name"
            exportedManga.description shouldBe "Description here"
            exportedManga.genre shouldBe listOf("Action", "Adventure")
            exportedManga.favorite shouldBe true
            exportedManga.notes shouldBe "My notes"
            exportedManga.initialized shouldBe true

            exportedManga.chapters.size shouldBe 1
            val exportedChapter = exportedManga.chapters.first()
            exportedChapter.name shouldBe "Chapter 1"
            exportedChapter.read shouldBe true
            exportedChapter.lastPageRead shouldBe 15L
            exportedChapter.chapterNumber shouldBe 1.0f

            exportedManga.history.size shouldBe 1
            val exportedHistory = exportedManga.history.first()
            exportedHistory.url shouldBe "/chapter/1"
            exportedHistory.lastRead shouldBe 5000L
            exportedHistory.readDuration shouldBe 300L

            exportedManga.tracking.size shouldBe 1
            val exportedTracking = exportedManga.tracking.first()
            exportedTracking.syncId shouldBe 1
            exportedTracking.mediaId shouldBe 999L
            exportedTracking.score shouldBe 8.5f
            exportedTracking.lastChapterRead shouldBe 1.0f

            exportedBackup.backupCategories.size shouldBe 1
            exportedBackup.backupCategories.first().name shouldBe "Favorites"

            exportedBackup.backupSources.size shouldBe 1
            exportedBackup.backupSources.first().name shouldBe "Test Source"

            exportedBackup.backupPreferences.size shouldBe 1
            exportedBackup.backupPreferences.first().key shouldBe "pref_display_mode_library"
            exportedBackup.backupPreferences.first().value shouldBe AndroidStringPreferenceValue("COMPACT_GRID")

            exportedBackup.backupSourcePreferences.size shouldBe 1
            exportedBackup.backupSourcePreferences.first().sourceKey shouldBe "test_src"
            exportedBackup.backupSourcePreferences.first().prefs.first().key shouldBe "quality"
            exportedBackup.backupSourcePreferences.first().prefs.first().value shouldBe
                AndroidStringPreferenceValue("HIGH")

            // Now export to file and re-import into a fresh database to verify round-trip file integrity
            val outputBackupFile = tempDir.resolve("output.tachibk")
            exporter.export(outputBackupFile)

            val dbFile2 = tempDir.resolve("roundtrip2.db")
            DesktopLibraryDatabaseFactory.open(dbFile2).use { repository2 ->
                val importer2 = AndroidBackupImporter(
                    codec = codec,
                    validator = AndroidBackupValidator(),
                    mutations = repository2,
                    preferences = SupportedPreferencePolicy(
                        appKeys = setOf("pref_display_mode_library"),
                        sourceKeys = mapOf("test_src" to setOf("quality")),
                    ),
                )
                val report2 = importer2.import(outputBackupFile, 20000L)
                report2.counts.mangaInserted shouldBe 1L
                report2.counts.chaptersInserted shouldBe 1L
                report2.counts.categoriesLinked shouldBe 1L

                repository2.librarySnapshot().first().title shouldBe "Roundtrip Manga"
            }
        }
    }
}
