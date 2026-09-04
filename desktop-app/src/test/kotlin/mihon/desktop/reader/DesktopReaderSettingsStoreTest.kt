package mihon.desktop.reader

import io.kotest.matchers.shouldBe
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.reader.model.ReadingMode
import mihon.reader.model.ScaleMode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class DesktopReaderSettingsStoreTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `missing preferences return reader defaults`() {
        val store = DesktopReaderSettingsStore(DesktopPreferenceStore(tempDir.resolve("preferences.properties")))

        store.load() shouldBe DesktopReaderSettings()
    }

    @Test
    fun `reader settings round trip through versioned keys`() {
        val file = tempDir.resolve("preferences.properties")
        val store = DesktopReaderSettingsStore(DesktopPreferenceStore(file))
        val expected = DesktopReaderSettings(
            mode = ReadingMode.DUAL_RTL,
            coverOffset = true,
            scaleMode = ScaleMode.FIT_HEIGHT,
            clickRegions = ClickRegions(
                leftAction = ReaderClickAction.NEXT,
                centerAction = ReaderClickAction.NONE,
                rightAction = ReaderClickAction.PREVIOUS,
                leftEndPercent = 30,
                centerEndPercent = 70,
            ),
            wheelBehavior = ReaderWheelBehavior.SCROLL,
            lastWindowMode = ReaderWindowMode.FULLSCREEN,
        )

        store.save(expected)

        DesktopReaderSettingsStore(DesktopPreferenceStore(file)).load() shouldBe expected
        Files.readString(file).contains("reader.v1.mode=DUAL_RTL") shouldBe true
    }

    @Test
    fun `corrupt and unknown reader values fall back independently`() {
        val file = tempDir.resolve("preferences.properties")
        Files.writeString(
            file,
            """
            reader.v1.mode=UNKNOWN
            reader.v1.cover-offset=not-a-boolean
            reader.v1.scale=FIT_HEIGHT
            reader.v1.click.left-action=INVALID
            reader.v1.click.center-action=NONE
            reader.v1.click.right-action=PREVIOUS
            reader.v1.click.left-end=0
            reader.v1.click.center-end=70
            reader.v1.wheel=SCROLL
            reader.v1.window=NOT_A_WINDOW
            theme=Dark
            """.trimIndent(),
        )

        val settings = DesktopReaderSettingsStore(DesktopPreferenceStore(file)).load()

        settings.mode shouldBe DesktopReaderSettings().mode
        settings.coverOffset shouldBe DesktopReaderSettings().coverOffset
        settings.scaleMode shouldBe ScaleMode.FIT_HEIGHT
        settings.clickRegions shouldBe DesktopReaderSettings().clickRegions
        settings.wheelBehavior shouldBe ReaderWheelBehavior.SCROLL
        settings.lastWindowMode shouldBe DesktopReaderSettings().lastWindowMode
        DesktopPreferenceStore(file).load().themeMode.name shouldBe "Dark"
    }
}
