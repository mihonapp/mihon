package mihon.desktop.preferences

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
)

class DesktopPreferenceStore(private val file: Path) {

    fun load(): DesktopPreferences {
        if (!Files.exists(file)) return DesktopPreferences()

        val properties = try {
            Properties().apply {
                Files.newInputStream(file).use { input -> load(input) }
            }
        } catch (_: IOException) {
            quarantineInvalidFile()
            return DesktopPreferences()
        } catch (_: IllegalArgumentException) {
            quarantineInvalidFile()
            return DesktopPreferences()
        }
        return DesktopPreferences(
            themeMode = enumValueOrDefault(properties.getProperty("theme"), ThemeMode.System),
            lastDestination = enumValueOrDefault(
                properties.getProperty("destination"),
                DesktopDestination.Library,
            ),
            windowPlacement = properties.readWindowPlacement(),
        )
    }

    fun save(preferences: DesktopPreferences) {
        Files.createDirectories(file.parent)
        val temporary = file.resolveSibling("${file.fileName}.tmp")
        val properties = Properties().apply {
            setProperty("theme", preferences.themeMode.name)
            setProperty("destination", preferences.lastDestination.name)
            preferences.windowPlacement?.let { placement ->
                setProperty("window.x", placement.x.toString())
                setProperty("window.y", placement.y.toString())
                setProperty("window.width", placement.width.toString())
                setProperty("window.height", placement.height.toString())
                setProperty("window.maximized", placement.maximized.toString())
            }
        }
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
