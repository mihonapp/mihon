package mihon.desktop.preferences

import io.kotest.matchers.shouldBe
import mihon.desktop.navigation.DesktopDestination
import mihon.desktop.window.WindowPlacement
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class DesktopPreferenceStoreTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `missing file returns safe defaults`() {
        DesktopPreferenceStore(tempDir.resolve("preferences.properties")).load() shouldBe DesktopPreferences()
    }

    @Test
    fun `saved theme and destination survive reload`() {
        val file = tempDir.resolve("preferences.properties")
        val store = DesktopPreferenceStore(file)
        val expected = DesktopPreferences(
            themeMode = ThemeMode.Dark,
            lastDestination = DesktopDestination.Browse,
            windowPlacement = WindowPlacement(120, 80, 1280, 800, maximized = true),
        )

        store.save(expected)

        DesktopPreferenceStore(file).load() shouldBe expected
        Files.exists(file.resolveSibling("preferences.properties.tmp")) shouldBe false
    }

    @Test
    fun `unknown enum values fall back independently`() {
        val file = tempDir.resolve("preferences.properties")
        Files.writeString(file, "theme=NEON\ndestination=UNKNOWN\n")

        DesktopPreferenceStore(file).load() shouldBe DesktopPreferences()
    }

    @Test
    fun `malformed properties return defaults and quarantine the original file`() {
        val file = tempDir.resolve("preferences.properties")
        val malformedProperties = "theme=\\u12G4\ndestination=Browse\n"
        Files.writeString(file, malformedProperties)

        DesktopPreferenceStore(file).load() shouldBe DesktopPreferences()

        Files.exists(file) shouldBe false
        val quarantinedFiles = Files.list(tempDir).use { files ->
            files.filter { it.fileName.toString().startsWith("preferences.properties.corrupt-") }.toList()
        }
        quarantinedFiles.size shouldBe 1
        Files.readString(quarantinedFiles.single()) shouldBe malformedProperties
    }
}
