package mihon.desktop.ui.reader

import io.kotest.matchers.shouldBe
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.desktop.reader.DesktopReaderSettings
import mihon.desktop.reader.DesktopReaderSettingsStore
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class DesktopReaderSettingsTransitionTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `transition and skip settings use mihon defaults`() {
        val store = DesktopReaderSettingsStore(DesktopPreferenceStore(tempDir.resolve("defaults.properties")))

        val settings = store.load()

        settings.alwaysShowChapterTransition shouldBe true
        settings.skipReadChapters shouldBe false
        settings.skipFilteredChapters shouldBe true
        settings.skipDuplicateChapters shouldBe false
    }

    @Test
    fun `transition and skip settings round trip through versioned keys`() {
        val file = tempDir.resolve("round-trip.properties")
        val store = DesktopReaderSettingsStore(DesktopPreferenceStore(file))
        val expected = DesktopReaderSettings(
            alwaysShowChapterTransition = true,
            skipReadChapters = true,
            skipFilteredChapters = true,
            skipDuplicateChapters = true,
        )

        store.save(expected)

        DesktopReaderSettingsStore(DesktopPreferenceStore(file)).load() shouldBe expected
        val contents = Files.readString(file)
        contents.contains("reader.v1.always-show-chapter-transition=true") shouldBe true
        contents.contains("reader.v1.skip-read=true") shouldBe true
        contents.contains("reader.v1.skip-filtered=true") shouldBe true
        contents.contains("reader.v1.skip-duplicate=true") shouldBe true
    }

    @Test
    fun `corrupt transition settings fall back independently`() {
        val file = tempDir.resolve("corrupt.properties")
        Files.writeString(
            file,
            """
            reader.v1.always-show-chapter-transition=maybe
            reader.v1.skip-read=true
            reader.v1.skip-filtered=not-a-bool
            reader.v1.skip-duplicate=true
            """.trimIndent(),
        )

        val settings = DesktopReaderSettingsStore(DesktopPreferenceStore(file)).load()

        settings.alwaysShowChapterTransition shouldBe true
        settings.skipReadChapters shouldBe true
        settings.skipFilteredChapters shouldBe true
        settings.skipDuplicateChapters shouldBe true
    }

    @Test
    fun `chapter bookmark store persists per chapter`() {
        val file = tempDir.resolve("bookmarks.properties")
        val store = DesktopReaderSettingsStore(DesktopPreferenceStore(file))
        val bookmarks = store.bookmarkStore()

        bookmarks.isBookmarked(42L) shouldBe false
        bookmarks.setBookmarked(42L, true)
        bookmarks.isBookmarked(42L) shouldBe true
        bookmarks.isBookmarked(43L) shouldBe false

        val reloaded = DesktopReaderSettingsStore(DesktopPreferenceStore(file)).bookmarkStore()
        reloaded.isBookmarked(42L) shouldBe true

        bookmarks.setBookmarked(42L, false)
        reloaded.isBookmarked(42L) shouldBe false
    }
}
