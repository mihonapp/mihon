package mihon.desktop.preferences

import io.kotest.matchers.shouldBe
import mihon.desktop.navigation.DesktopDestination
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
        val expected = DesktopPreferences(ThemeMode.Dark, DesktopDestination.Browse)

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
}
