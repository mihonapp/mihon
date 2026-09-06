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
        )
    }

    @Synchronized
    fun save(preferences: DesktopPreferences) {
        val properties = readProperties()
        properties.setProperty("theme", preferences.themeMode.name)
        properties.setProperty("destination", preferences.lastDestination.name)
        properties.setProperty("language", preferences.language.code)
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
