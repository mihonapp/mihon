package mihon.desktop.library.update

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import mihon.desktop.extension.DesktopSourceManager
import mihon.desktop.extension.OnlineMangaSyncService
import mihon.desktop.library.db.DesktopLibraryDatabaseFactory
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.desktop.preferences.DesktopPreferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class LibraryUpdateSchedulerTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `auto update runs only when interval has elapsed`() = runBlocking {
        val dbFile = tempDir.resolve("scheduler-test.db")
        val repo = DesktopLibraryDatabaseFactory.open(dbFile)
        val prefFile = tempDir.resolve("prefs.properties")
        val prefStore = DesktopPreferenceStore(prefFile)

        try {
            val sourceManager = DesktopSourceManager()
            val syncService = OnlineMangaSyncService(repo, sourceManager)
            val updateService = LibraryUpdateService(repo, syncService)

            var simulatedTime = 1_000_000_000L
            val scope = CoroutineScope(SupervisorJob())

            val scheduler = LibraryUpdateScheduler(
                updateService = updateService,
                preferenceStore = prefStore,
                scope = scope,
                clock = { simulatedTime },
            )

            // 1. Interval = 0 (Disabled/Manual)
            prefStore.save(DesktopPreferences(libraryUpdateIntervalHours = 0))
            assertNull(scheduler.checkAndRunAutoUpdate())

            // 2. Interval = 12 hours, last update was at simulatedTime
            prefStore.save(
                DesktopPreferences(
                    libraryUpdateIntervalHours = 12,
                    lastLibraryUpdateEpochMillis = simulatedTime,
                ),
            )
            // Advance by 5 hours (< 12 hours)
            simulatedTime += 5 * 3_600_000L
            assertNull(scheduler.checkAndRunAutoUpdate())

            // 3. Advance by 8 more hours (total 13 hours > 12 hours)
            simulatedTime += 8 * 3_600_000L
            val report = scheduler.checkAndRunAutoUpdate()
            assertNotNull(report)
            assertEquals(simulatedTime, prefStore.load().lastLibraryUpdateEpochMillis)

            // 4. triggerUpdateNow forces update even if 0 time has passed
            val forceReport = scheduler.triggerUpdateNow()
            assertNotNull(forceReport)
        } finally {
            repo.close()
        }
    }
}
