package mihon.desktop.backup

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import mihon.desktop.library.backup.AndroidBackupExporter
import mihon.desktop.library.db.DesktopLibraryDatabaseFactory
import mihon.desktop.library.model.CategoryRecord
import mihon.desktop.library.model.ChapterRecord
import mihon.desktop.library.model.HistoryRecord
import mihon.desktop.library.model.HistoryWithDetails
import mihon.desktop.library.model.LibraryChapter
import mihon.desktop.library.model.LibraryManga
import mihon.desktop.library.model.MangaDetails
import mihon.desktop.library.model.MangaRecord
import mihon.desktop.library.model.PreferenceSnapshotRecord
import mihon.desktop.library.model.SourcePreferenceSnapshotRecord
import mihon.desktop.library.model.SourceRecord
import mihon.desktop.library.model.TrackingRecord
import mihon.desktop.library.repository.LibraryRepository
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.desktop.preferences.DesktopPreferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime

class DesktopBackupSchedulerTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `auto backup triggers only when interval has elapsed`() = runBlocking {
        val dbFile = tempDir.resolve("backup-test.db")
        val repo = DesktopLibraryDatabaseFactory.open(dbFile)

        try {
            val exporter = AndroidBackupExporter(repo)
            val prefFile = tempDir.resolve("prefs.properties")
            val prefStore = DesktopPreferenceStore(prefFile)
            val backupDir = tempDir.resolve("backups")

            var simulatedTime = 1_000_000_000L
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

            val scheduler = DesktopBackupScheduler(
                backupExporter = exporter,
                preferenceStore = prefStore,
                defaultBackupDir = backupDir,
                scope = scope,
                clock = { simulatedTime },
            )

            // 1. Interval is 0 (Disabled)
            prefStore.save(DesktopPreferences(backupIntervalHours = 0))
            assertNull(scheduler.checkAndRunAutoBackup())

            // 2. Interval is 6 hours, last backup was at simulatedTime
            prefStore.save(
                DesktopPreferences(
                    backupIntervalHours = 6,
                    lastAutoBackupEpochMillis = simulatedTime,
                ),
            )
            // Advance by 3 hours (less than 6 hours)
            simulatedTime += 3 * 3_600_000L
            assertNull(scheduler.checkAndRunAutoBackup())

            // Advance by 4 more hours (total 7 hours > 6 hours)
            simulatedTime += 4 * 3_600_000L
            val backupFile = scheduler.checkAndRunAutoBackup()
            assertNotNull(backupFile)
            assertTrue(Files.exists(backupFile!!))
            assertEquals(simulatedTime, prefStore.load().lastAutoBackupEpochMillis)
        } finally {
            repo.close()
        }
    }

    @Test
    fun `pruneOldBackups removes oldest backups keeping only up to retention count`() {
        val backupDir = tempDir.resolve("prune-backups")
        Files.createDirectories(backupDir)

        val files = (1..5).map { i ->
            val path = backupDir.resolve("mihon_backup_2026-09-0$i.tachibk")
            Files.writeString(path, "backup-$i")
            Files.setLastModifiedTime(path, FileTime.fromMillis(i * 1000L))
            path
        }

        val prefStore = DesktopPreferenceStore(tempDir.resolve("prefs.properties"))
        val scope = CoroutineScope(SupervisorJob())
        val scheduler = DesktopBackupScheduler(
            backupExporter = AndroidBackupExporter(FakeLibraryRepository()),
            preferenceStore = prefStore,
            defaultBackupDir = backupDir,
            scope = scope,
        )

        // Prune to 2 backups
        scheduler.pruneOldBackups(backupDir, maxRetention = 2)

        val remainingFiles = Files.list(backupDir).use { stream ->
            stream.map { it.fileName.toString() }.toList().sorted()
        }
        assertEquals(2, remainingFiles.size)
        // Kept the two newest (i=4, i=5)
        assertEquals(listOf("mihon_backup_2026-09-04.tachibk", "mihon_backup_2026-09-05.tachibk"), remainingFiles)
    }

    private class FakeLibraryRepository : LibraryRepository {
        override fun observeLibrary(categoryId: Long?) = emptyFlow<List<LibraryManga>>()
        override fun observeManga(id: Long) = emptyFlow<MangaDetails?>()
        override fun observeChapters(mangaId: Long) = emptyFlow<List<LibraryChapter>>()
        override fun observeCategories() = emptyFlow<List<CategoryRecord>>()
        override fun observeHistory(query: String) = emptyFlow<List<HistoryWithDetails>>()
        override fun observeTracking(mangaId: Long) = emptyFlow<List<TrackingRecord>>()
        override fun librarySnapshot(categoryId: Long?) = emptyList<LibraryManga>()
        override fun mangaSnapshot(id: Long) = null
        override fun chapterSnapshot(mangaId: Long) = emptyList<LibraryChapter>()
        override fun categoriesSnapshot() = emptyList<CategoryRecord>()
        override fun historySnapshot(query: String) = emptyList<HistoryWithDetails>()
        override fun trackingSnapshot(mangaId: Long) = emptyList<TrackingRecord>()
        override fun latestImportReport() = null
        override fun allMangaSnapshot() = emptyList<MangaRecord>()
        override fun allChaptersSnapshot() = emptyList<ChapterRecord>()
        override fun allCategoriesSnapshot() = emptyList<CategoryRecord>()
        override fun mangaCategoryLinksSnapshot() = emptyMap<Long, List<Long>>()
        override fun allHistorySnapshot() = emptyList<HistoryRecord>()
        override fun allTrackingSnapshot() = emptyList<TrackingRecord>()
        override fun allSourcesSnapshot() = emptyList<SourceRecord>()
        override fun allPreferenceSnapshots() = emptyList<PreferenceSnapshotRecord>()
        override fun allSourcePreferenceSnapshots() = emptyList<SourcePreferenceSnapshotRecord>()
        override fun checkIntegrity() = emptyList<String>()
    }
}
