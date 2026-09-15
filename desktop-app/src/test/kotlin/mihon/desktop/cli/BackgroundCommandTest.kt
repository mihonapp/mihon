package mihon.desktop.cli

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import mihon.desktop.DesktopRuntime
import mihon.desktop.backup.DesktopBackupScheduler
import mihon.desktop.extension.DesktopSourceManager
import mihon.desktop.extension.OnlineMangaSyncService
import mihon.desktop.library.backup.AndroidBackupCodec
import mihon.desktop.library.backup.AndroidBackupExporter
import mihon.desktop.library.backup.AndroidBackupImporter
import mihon.desktop.library.backup.AndroidBackupValidator
import mihon.desktop.library.db.DesktopLibraryDatabaseFactory
import mihon.desktop.library.local.LocalMangaImporter
import mihon.desktop.library.update.LibraryUpdateScheduler
import mihon.desktop.library.update.LibraryUpdateService
import mihon.desktop.platform.AppDirectories
import mihon.desktop.preferences.DesktopPreferenceStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path

class BackgroundCommandTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `headless update and backup obey disabled intervals then run once when due`() {
        val paths = AppDirectories(directory).create()
        val library = DesktopLibraryDatabaseFactory.open(paths.database.resolve("library.db"))
        val preferences = DesktopPreferenceStore(directory.resolve("preferences.properties"))
        val scope = CoroutineScope(SupervisorJob())
        val now = 100_000_000L
        val scheduler = LibraryUpdateScheduler(
            LibraryUpdateService(library, OnlineMangaSyncService(library, DesktopSourceManager())),
            preferences,
            scope,
            { now },
            directory.resolve("update-state.properties"),
            false,
        )
        val backups =
            DesktopBackupScheduler(AndroidBackupExporter(library), preferences, directory.resolve("backups"), scope, {
                now
            })
        val runtime = DesktopRuntime(
            directories = paths,
            preferences = preferences,
            command = DesktopCommand.BackgroundUpdate,
            library = library,
            backupImporter = AndroidBackupImporter(AndroidBackupCodec(), AndroidBackupValidator(), library),
            localImporter = LocalMangaImporter(library),
            localLibraryRoot = directory.resolve("local"),
            libraryUpdateScheduler = scheduler,
            backupScheduler = backups,
            backgroundScheduler = mihon.desktop.platform.WindowsBackgroundScheduler(
                directory.resolve("MihonW.exe"),
                directory,
                mihon.desktop.platform.BackgroundProcessRunner { args ->
                    mihon.desktop.platform.BackgroundProcessResult(
                        0,
                        if (args.first() ==
                            "/Query"
                        ) {
                            "<Task/>"
                        } else {
                            "deleted"
                        },
                    )
                },
            ),
        )
        val output = ByteArrayOutputStream()
        try {
            val runner = DesktopCommandRunner(runtime, output)
            assertEquals(0, runner.run(DesktopCommand.RemoveBackgroundTasks))
            assertTrue(output.toString(Charsets.UTF_8).contains("SUCCEEDED"))
            assertTrue(Files.exists(paths.database.resolve("library.db")))
            output.reset()
            assertEquals(0, runner.run(DesktopCommand.BackgroundUpdate))
            assertTrue(output.toString(Charsets.UTF_8).contains("SKIPPED"))
            output.reset()
            assertEquals(0, runner.run(DesktopCommand.BackgroundBackup))
            assertTrue(output.toString(Charsets.UTF_8).contains("SKIPPED"))
            preferences.save(preferences.load().copy(libraryUpdateIntervalHours = 1, backupIntervalHours = 1))
            output.reset()
            assertEquals(0, runner.run(DesktopCommand.BackgroundUpdate))
            assertTrue(output.toString(Charsets.UTF_8).contains("SUCCEEDED"))
            output.reset()
            assertEquals(0, runner.run(DesktopCommand.BackgroundBackup))
            assertTrue(output.toString(Charsets.UTF_8).contains("SUCCEEDED"))
            output.reset()
            assertEquals(0, runner.run(DesktopCommand.BackgroundBackup))
            assertTrue(output.toString(Charsets.UTF_8).contains("SKIPPED"))
            Files.list(directory.resolve("backups")).use { assertEquals(1L, it.count()) }
        } finally {
            scope.cancel()
            runtime.close()
        }
    }
}
