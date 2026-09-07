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
            language = mihon.desktop.i18n.AppLanguage.SimplifiedChinese,
        )

        store.save(expected)

        DesktopPreferenceStore(file).load() shouldBe expected
        Files.exists(file.resolveSibling("preferences.properties.tmp")) shouldBe false
    }

    @Test
    fun `language setting can be saved and restored`() {
        val file = tempDir.resolve("preferences.properties")
        val store = DesktopPreferenceStore(file)
        store.save(DesktopPreferences(language = mihon.desktop.i18n.AppLanguage.TraditionalChinese))
        store.load().language shouldBe mihon.desktop.i18n.AppLanguage.TraditionalChinese
    }

    @Test
    fun `unknown enum values fall back independently`() {
        val file = tempDir.resolve("preferences.properties")
        Files.writeString(file, "theme=NEON\ndestination=UNKNOWN\n")

        DesktopPreferenceStore(file).load() shouldBe DesktopPreferences()
    }

    @Test
    fun `property updates preserve unrelated versioned reader preferences`() {
        val file = tempDir.resolve("preferences.properties")
        val store = DesktopPreferenceStore(file)

        store.update { setProperty("reader.v1.mode", "DUAL_RTL") }
        store.save(DesktopPreferences(themeMode = ThemeMode.Dark))

        store.property("reader.v1.mode") shouldBe "DUAL_RTL"
        DesktopPreferenceStore(file).load().themeMode shouldBe ThemeMode.Dark
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

    @Test
    fun `incognito and backup preferences can be saved and restored`() {
        val file = tempDir.resolve("preferences.properties")
        val store = DesktopPreferenceStore(file)
        val expected = DesktopPreferences(
            incognitoMode = true,
            backupIntervalHours = 24,
            backupStoragePath = "D:/custom/backups",
            backupRetentionCount = 5,
            lastAutoBackupEpochMillis = 123456789L,
        )
        store.save(expected)
        val loaded = store.load()
        loaded.incognitoMode shouldBe true
        loaded.backupIntervalHours shouldBe 24
        loaded.backupStoragePath shouldBe "D:/custom/backups"
        loaded.backupRetentionCount shouldBe 5
        loaded.lastAutoBackupEpochMillis shouldBe 123456789L
    }

    @Test
    fun `library update and notification preferences can be saved and restored`() {
        val file = tempDir.resolve("preferences.properties")
        val store = DesktopPreferenceStore(file)
        val expected = DesktopPreferences(
            libraryUpdateIntervalHours = 12,
            libraryUpdateSkipCompleted = false,
            libraryUpdateSkipUnread = true,
            autoDownloadNewChapters = true,
            desktopNotificationsEnabled = false,
            lastLibraryUpdateEpochMillis = 987654321L,
        )
        store.save(expected)
        val loaded = store.load()
        loaded.libraryUpdateIntervalHours shouldBe 12
        loaded.libraryUpdateSkipCompleted shouldBe false
        loaded.libraryUpdateSkipUnread shouldBe true
        loaded.autoDownloadNewChapters shouldBe true
        loaded.desktopNotificationsEnabled shouldBe false
        loaded.lastLibraryUpdateEpochMillis shouldBe 987654321L
    }
}
