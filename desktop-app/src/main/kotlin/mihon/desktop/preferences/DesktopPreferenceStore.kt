package mihon.desktop.preferences

import mihon.desktop.i18n.AppLanguage
import mihon.desktop.navigation.DesktopDestination
import mihon.desktop.window.WindowPlacement
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.Properties
import java.util.UUID

enum class ThemeMode {
    System,
    Light,
    Dark,
}

data class DesktopPreferences(
    val themeMode: ThemeMode = ThemeMode.System,
    val lastDestination: DesktopDestination = DesktopDestination.Library,
    val windowPlacement: WindowPlacement? = null,
    val language: AppLanguage = AppLanguage.System,
    val libraryDisplayMode: String = "ComfortableGrid",
    val libraryGridSize: Float = 180f,
    val librarySortMode: String = "None",
    val librarySortAscending: Boolean = true,
    val libraryFilterUnread: String = "Disabled",
    val libraryFilterDownloaded: String = "Disabled",
    val libraryFilterStarted: String = "Disabled",
    val libraryFilterCompleted: String = "Disabled",
    val libraryFilterBookmarked: String = "Disabled",
    val downloadStoragePath: String = "",
    val downloadParallelCount: Int = 3,
    val downloadAhead: Int = 0,
    val deleteDownloadedRead: Boolean = false,
    val incognitoMode: Boolean = false,
    val backupIntervalHours: Int = 0,
    val backupStoragePath: String = "",
    val backupRetentionCount: Int = 10,
    val lastAutoBackupEpochMillis: Long = 0L,
    val libraryUpdateIntervalHours: Int = 0,
    val libraryUpdateSkipCompleted: Boolean = true,
    val libraryUpdateSkipUnread: Boolean = false,
    val autoDownloadNewChapters: Boolean = false,
    val desktopNotificationsEnabled: Boolean = true,
    val lastLibraryUpdateEpochMillis: Long = 0L,
)

class DesktopPreferenceStore(private val file: Path) {

    @Synchronized
    fun load(): DesktopPreferences {
        val properties = readProperties()
        return DesktopPreferences(
            themeMode = enumValueOrDefault(properties.getProperty("theme"), ThemeMode.System),
            lastDestination = enumValueOrDefault(
                properties.getProperty("destination"),
                DesktopDestination.Library,
            ),
            windowPlacement = properties.readWindowPlacement(),
            language = AppLanguage.fromCode(properties.getProperty("language")),
            libraryDisplayMode = properties.getProperty("library.display_mode") ?: "ComfortableGrid",
            libraryGridSize = properties.getProperty("library.grid_size")?.toFloatOrNull() ?: 180f,
            librarySortMode = properties.getProperty("library.sort_mode") ?: "None",
            librarySortAscending = properties.getProperty("library.sort_ascending")?.toBooleanStrictOrNull() ?: true,
            libraryFilterUnread = properties.getProperty("library.filter_unread") ?: "Disabled",
            libraryFilterDownloaded = properties.getProperty("library.filter_downloaded") ?: "Disabled",
            libraryFilterStarted = properties.getProperty("library.filter_started") ?: "Disabled",
            libraryFilterCompleted = properties.getProperty("library.filter_completed") ?: "Disabled",
            libraryFilterBookmarked = properties.getProperty("library.filter_bookmarked") ?: "Disabled",
            downloadStoragePath = properties.getProperty("download.storage_path") ?: "",
            downloadParallelCount = properties.getProperty("download.parallel_count")?.toIntOrNull() ?: 3,
            downloadAhead = properties.getProperty("download.ahead")?.toIntOrNull() ?: 0,
            deleteDownloadedRead = properties.getProperty("download.delete_read")?.toBooleanStrictOrNull() ?: false,
            incognitoMode = properties.getProperty("security.incognito_mode")?.toBooleanStrictOrNull() ?: false,
            backupIntervalHours = properties.getProperty("backup.interval_hours")?.toIntOrNull() ?: 0,
            backupStoragePath = properties.getProperty("backup.storage_path") ?: "",
            backupRetentionCount = properties.getProperty("backup.retention_count")?.toIntOrNull() ?: 10,
            lastAutoBackupEpochMillis = properties.getProperty("backup.last_epoch_millis")?.toLongOrNull() ?: 0L,
            libraryUpdateIntervalHours = properties.getProperty("library.update_interval_hours")?.toIntOrNull() ?: 0,
            libraryUpdateSkipCompleted = properties.getProperty("library.update_skip_completed")
                ?.toBooleanStrictOrNull() ?: true,
            libraryUpdateSkipUnread = properties.getProperty("library.update_skip_unread")
                ?.toBooleanStrictOrNull() ?: false,
            autoDownloadNewChapters = properties.getProperty("library.auto_download_new")
                ?.toBooleanStrictOrNull() ?: false,
            desktopNotificationsEnabled = properties.getProperty("notifications.desktop_enabled")
                ?.toBooleanStrictOrNull() ?: true,
            lastLibraryUpdateEpochMillis = properties.getProperty("library.last_update_epoch_millis")
                ?.toLongOrNull() ?: 0L,
        )
    }

    @Synchronized
    fun save(preferences: DesktopPreferences) {
        val properties = readProperties()
        properties.setProperty("theme", preferences.themeMode.name)
        properties.setProperty("destination", preferences.lastDestination.name)
        properties.setProperty("language", preferences.language.code)
        properties.setProperty("library.display_mode", preferences.libraryDisplayMode)
        properties.setProperty("library.grid_size", preferences.libraryGridSize.toString())
        properties.setProperty("library.sort_mode", preferences.librarySortMode)
        properties.setProperty("library.sort_ascending", preferences.librarySortAscending.toString())
        properties.setProperty("library.filter_unread", preferences.libraryFilterUnread)
        properties.setProperty("library.filter_downloaded", preferences.libraryFilterDownloaded)
        properties.setProperty("library.filter_started", preferences.libraryFilterStarted)
        properties.setProperty("library.filter_completed", preferences.libraryFilterCompleted)
        properties.setProperty("library.filter_bookmarked", preferences.libraryFilterBookmarked)
        properties.setProperty("download.storage_path", preferences.downloadStoragePath)
        properties.setProperty("download.parallel_count", preferences.downloadParallelCount.toString())
        properties.setProperty("download.ahead", preferences.downloadAhead.toString())
        properties.setProperty("download.delete_read", preferences.deleteDownloadedRead.toString())
        properties.setProperty("security.incognito_mode", preferences.incognitoMode.toString())
        properties.setProperty("backup.interval_hours", preferences.backupIntervalHours.toString())
        properties.setProperty("backup.storage_path", preferences.backupStoragePath)
        properties.setProperty("backup.retention_count", preferences.backupRetentionCount.toString())
        properties.setProperty("backup.last_epoch_millis", preferences.lastAutoBackupEpochMillis.toString())
        properties.setProperty(
            "library.update_interval_hours",
            preferences.libraryUpdateIntervalHours.toString(),
        )
        properties.setProperty(
            "library.update_skip_completed",
            preferences.libraryUpdateSkipCompleted.toString(),
        )
        properties.setProperty(
            "library.update_skip_unread",
            preferences.libraryUpdateSkipUnread.toString(),
        )
        properties.setProperty(
            "library.auto_download_new",
            preferences.autoDownloadNewChapters.toString(),
        )
        properties.setProperty(
            "notifications.desktop_enabled",
            preferences.desktopNotificationsEnabled.toString(),
        )
        properties.setProperty(
            "library.last_update_epoch_millis",
            preferences.lastLibraryUpdateEpochMillis.toString(),
        )
        properties.remove("window.x")
        properties.remove("window.y")
        properties.remove("window.width")
        properties.remove("window.height")
        properties.remove("window.maximized")
        preferences.windowPlacement?.let { placement ->
            properties.setProperty("window.x", placement.x.toString())
            properties.setProperty("window.y", placement.y.toString())
            properties.setProperty("window.width", placement.width.toString())
            properties.setProperty("window.height", placement.height.toString())
            properties.setProperty("window.maximized", placement.maximized.toString())
        }
        writeProperties(properties)
    }

    @Synchronized
    fun property(key: String): String? = readProperties().getProperty(key)

    @Synchronized
    fun update(block: Properties.() -> Unit) {
        writeProperties(readProperties().apply(block))
    }

    private fun readProperties(): Properties {
        if (!Files.exists(file)) return Properties()
        return try {
            Properties().apply {
                Files.newInputStream(file).use { input -> load(input) }
            }
        } catch (_: IOException) {
            quarantineInvalidFile()
            Properties()
        } catch (_: IllegalArgumentException) {
            quarantineInvalidFile()
            Properties()
        }
    }

    private fun writeProperties(properties: Properties) {
        Files.createDirectories(file.parent)
        val temporary = file.resolveSibling("${file.fileName}.tmp")
        Files.newOutputStream(temporary).use { properties.store(it, "Mihon W desktop preferences") }
        try {
            Files.move(temporary, file, ATOMIC_MOVE, REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary, file, REPLACE_EXISTING)
        }
    }

    private fun quarantineInvalidFile() {
        val corruptFile = file.resolveSibling("${file.fileName}.corrupt-${UUID.randomUUID()}")
        try {
            Files.move(file, corruptFile)
        } catch (_: IOException) {
            // The original file remains in place for diagnosis when it cannot be moved.
        } catch (_: SecurityException) {
            // The original file remains in place for diagnosis when permissions prevent moving it.
        }
    }

    private fun Properties.readWindowPlacement(): WindowPlacement? {
        val x = getProperty("window.x")?.toIntOrNull() ?: return null
        val y = getProperty("window.y")?.toIntOrNull() ?: return null
        val width = getProperty("window.width")?.toIntOrNull() ?: return null
        val height = getProperty("window.height")?.toIntOrNull() ?: return null
        val maximized = getProperty("window.maximized")?.toBooleanStrictOrNull() ?: false
        return WindowPlacement(x, y, width, height, maximized)
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, default: T): T {
        return enumValues<T>().firstOrNull { it.name == value } ?: default
    }
}
