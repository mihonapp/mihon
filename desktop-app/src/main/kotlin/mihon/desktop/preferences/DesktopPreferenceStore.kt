package mihon.desktop.preferences

import mihon.desktop.navigation.DesktopDestination
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.Properties

enum class ThemeMode {
    System,
    Light,
    Dark,
}

data class DesktopPreferences(
    val themeMode: ThemeMode = ThemeMode.System,
    val lastDestination: DesktopDestination = DesktopDestination.Library,
)

class DesktopPreferenceStore(private val file: Path) {

    fun load(): DesktopPreferences {
        if (!Files.exists(file)) return DesktopPreferences()

        val properties = Properties().apply {
            Files.newInputStream(file).use { input -> load(input) }
        }
        return DesktopPreferences(
            themeMode = enumValueOrDefault(properties.getProperty("theme"), ThemeMode.System),
            lastDestination = enumValueOrDefault(
                properties.getProperty("destination"),
                DesktopDestination.Library,
            ),
        )
    }

    fun save(preferences: DesktopPreferences) {
        Files.createDirectories(file.parent)
        val temporary = file.resolveSibling("${file.fileName}.tmp")
        val properties = Properties().apply {
            setProperty("theme", preferences.themeMode.name)
            setProperty("destination", preferences.lastDestination.name)
        }
        Files.newOutputStream(temporary).use { properties.store(it, "Mihon W desktop preferences") }
        try {
            Files.move(temporary, file, ATOMIC_MOVE, REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary, file, REPLACE_EXISTING)
        }
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, default: T): T {
        return enumValues<T>().firstOrNull { it.name == value } ?: default
    }
}
