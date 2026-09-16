package mihon.desktop.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import mihon.desktop.preferences.ThemeMode
import mihon.desktop.ui.MihonDesktopTheme
import mihon.desktop.ui.theme.DesktopAppTheme
import mihon.desktop.ui.theme.ThemeRegistry
import org.jetbrains.skia.Image
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.abs

@OptIn(ExperimentalTestApi::class)
class ReaderChromeContrastTest {
    @Test
    fun `pure black reader keeps title page labels and icons visible`() = verifyContrast(ThemeMode.Dark, true)

    @Test
    fun `dark reader keeps title page labels and icons visible`() = verifyContrast(ThemeMode.Dark, false)

    @Test
    fun `light reader keeps title page labels and icons visible`() = verifyContrast(ThemeMode.Light, false)

    private fun verifyContrast(mode: ThemeMode, amoled: Boolean) = runComposeUiTest {
        System.getenv("MIHON_READER_INSTALLED_APP")?.let { directory ->
            val loadedFrom = Path.of(
                Class.forName("mihon.desktop.ui.reader.ReaderScreenKt").protectionDomain.codeSource.location.toURI(),
            )
            assertTrue(
                loadedFrom.startsWith(Path.of(directory)),
                "ReaderScreen must come from the installed app: $loadedFrom",
            )
            println("INSTALLED_READER_UI classes=$loadedFrom theme=$mode amoled=$amoled")
        }
        val scheme = ThemeRegistry.getColorScheme(DesktopAppTheme.DEFAULT)
            .getColorScheme(isDark = mode == ThemeMode.Dark, isAmoled = amoled)
        setContent {
            // No parent Surface: the reader destination renders directly inside the app theme.
            MihonDesktopTheme(themeMode = mode, isAmoled = amoled) {
                Box(Modifier.requiredSize(1024.dp, 720.dp).background(Color.Black)) {
                    ReaderScreen(
                        session = TestReaderSession(testReaderState(pageCount = 48, selectedIndex = 23)),
                        title = "Reader contrast check / 阅读器标题",
                        chapterTitle = "Chapter 24 / 第 24 章",
                        settingsStore = null,
                        onBack = {},
                        mangaId = 1L,
                    )
                }
            }
        }

        // Also retain a desktop-width rendering for manual visual inspection when requested.
        System.getenv("MIHON_READER_CONTRAST_EVIDENCE")?.let { directory ->
            val target = Path.of(directory)
            Files.createDirectories(target)
            Image.makeFromBitmap(onRoot().captureToImage().asSkiaBitmap()).use { image ->
                image.encodeToData()!!.use { png ->
                    Files.write(target.resolve("reader-${mode.name.lowercase()}-$amoled.png"), png.bytes)
                }
            }
        }
        for (tag in listOf("reader-title", "reader-current-page", "reader-total-pages")) {
            val layouts = mutableListOf<TextLayoutResult>()
            onNodeWithTag(tag, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
                it(layouts)
            }
            assertEquals(scheme.onSurface, layouts.single().layoutInput.style.color, "$tag foreground")
        }
        for (tag in listOf("reader-back", "reader-bookmark-toggle", "reader-overflow", "reader-settings")) {
            val pixels = onNodeWithTag(tag).captureToImage().toPixelMap()
            val foregroundPixels = (0 until pixels.height).sumOf { y ->
                (0 until pixels.width).count { x ->
                    val pixel = pixels[x, y]
                    abs(pixel.red - scheme.onSurface.red) < 0.06f &&
                        abs(pixel.green - scheme.onSurface.green) < 0.06f &&
                        abs(pixel.blue - scheme.onSurface.blue) < 0.06f
                }
            }
            // The three-dot overflow glyph has only a few fully covered pixels at 1x density.
            assertTrue(foregroundPixels >= 6, "$tag must render a visible foreground icon ($foregroundPixels pixels)")
        }
    }
}
