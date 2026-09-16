package mihon.desktop.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import mihon.desktop.i18n.AppLanguage
import mihon.desktop.i18n.ProvideDesktopStrings
import mihon.desktop.preferences.ThemeMode
import mihon.desktop.ui.MihonDesktopTheme
import mihon.desktop.ui.library.ChapterSettings
import mihon.desktop.ui.library.ChapterSettingsDialog
import mihon.desktop.ui.upcoming.UpcomingScreen
import mihon.desktop.ui.upcoming.UpcomingUiState
import org.jetbrains.skia.Image
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.time.YearMonth

@OptIn(ExperimentalTestApi::class)
class DesktopLocalizationUiTest {
    @Test
    fun `open reader menu follows language changes without restarting`() = runComposeUiTest {
        verifyInstalledClasses()
        val language = mutableStateOf(AppLanguage.English)
        setContent {
            LocalizedTheme(language.value) {
                ReaderScreen(
                    session = TestReaderSession(testReaderState(pageCount = 48, selectedIndex = 23)),
                    title = "Source title stays unchanged",
                    chapterTitle = "Chapter from source",
                    settingsStore = null,
                    mangaId = 1L,
                    onBack = {},
                )
            }
        }
        onNodeWithTag("reader-overflow").performClick()
        onNodeWithText("Page actions").assertIsDisplayed()
        runOnIdle { language.value = AppLanguage.SimplifiedChinese }
        onNodeWithText("图片操作").assertIsDisplayed()
        onNodeWithText("缩小").assertIsDisplayed()
        onNodeWithText("Page actions").assertDoesNotExist()
        onNodeWithText("Source title stays unchanged").assertIsDisplayed()
        saveRendering("reader-menu-zh")
        runOnIdle { language.value = AppLanguage.TraditionalChinese }
        onNodeWithText("圖片操作").assertIsDisplayed()
        runOnIdle { language.value = AppLanguage.English }
        onNodeWithText("Page actions").assertIsDisplayed()
    }

    @Test
    fun `page actions use Chinese labels`() = runComposeUiTest {
        setContent {
            LocalizedTheme {
                ReaderPageActionsDialog(
                    onDismissRequest = {},
                    onSave = {},
                    onCopy = {},
                    onShare = {},
                    onSetAsCover = {},
                    onOpenInBrowser = {},
                    canOpenInBrowser = true,
                )
            }
        }
        onNodeWithText("保存图片").assertIsDisplayed()
        onNodeWithText("复制图片").assertIsDisplayed()
        onNodeWithText("在浏览器中打开图片").assertIsDisplayed()
        onNodeWithText("取消").assertIsDisplayed()
        saveRendering("page-actions-zh")
    }

    @Test
    fun `chapter settings use Chinese section and filter labels`() = runComposeUiTest {
        setContent {
            LocalizedTheme {
                ChapterSettingsDialog(
                    settings = ChapterSettings(), availableScanlators = setOf("Original group"),
                    onDismissRequest = {}, onDisplayModeChange = {}, onSortModeChange = { _, _ -> },
                    onShowMissingChaptersChange = {}, onExcludedScanlatorsChange = {},
                    onSetAsDefault = {}, onResetToDefault = {},
                )
            }
        }
        onNodeWithText("章节设置").assertIsDisplayed()
        onNodeWithText("翻译组筛选").assertIsDisplayed()
        saveRendering("chapter-settings-zh")
        onNodeWithTag("chapter-settings-scanlators").performClick()
        onNodeWithText("排除翻译组").assertIsDisplayed()
        onNodeWithText("Original group").assertIsDisplayed()
    }

    @Test
    fun `upcoming calendar and empty message follow selected language`() = runComposeUiTest {
        setContent {
            LocalizedTheme {
                UpcomingScreen(state = UpcomingUiState(loading = false, selectedMonth = YearMonth.of(2026, 9)))
            }
        }
        onNodeWithText("更新日历").assertIsDisplayed()
        val titleLayouts = mutableListOf<TextLayoutResult>()
        onNodeWithText("更新日历").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(titleLayouts) }
        assertEquals(Color.White, titleLayouts.single().layoutInput.style.color)
        onNodeWithText("暂无待更新章节").assertIsDisplayed()
        onNodeWithTag("upcoming_month_header").assertIsDisplayed()
        onNodeWithText("No upcoming chapters").assertDoesNotExist()
        saveRendering("calendar-zh")
    }

    private fun verifyInstalledClasses() {
        System.getenv("MIHON_READER_INSTALLED_APP")?.let { directory ->
            val loadedFrom = Path.of(
                Class.forName("mihon.desktop.ui.reader.ReaderScreenKt").protectionDomain.codeSource.location.toURI(),
            )
            assertTrue(loadedFrom.startsWith(Path.of(directory)), "Expected installed reader: $loadedFrom")
            println("INSTALLED_LOCALIZATION_UI classes=$loadedFrom")
        }
    }

    private fun ComposeUiTest.saveRendering(name: String) {
        System.getenv("MIHON_LOCALIZATION_EVIDENCE")?.let { directory ->
            val target = Path.of(directory)
            Files.createDirectories(target)
            // Popups and dialogs may add another root; capture the one containing the visible content.
            val roots = onAllNodes(isRoot())
            val visibleRoot = roots[roots.fetchSemanticsNodes().lastIndex]
            Image.makeFromBitmap(visibleRoot.captureToImage().asSkiaBitmap()).use { image ->
                image.encodeToData()!!.use { png -> Files.write(target.resolve("$name.png"), png.bytes) }
            }
        }
    }
}

@Composable
private fun LocalizedTheme(language: AppLanguage = AppLanguage.SimplifiedChinese, content: @Composable () -> Unit) {
    ProvideDesktopStrings(language) {
        MihonDesktopTheme(themeMode = ThemeMode.Dark, isAmoled = true) {
            Box(Modifier.requiredSize(1024.dp, 720.dp)) { content() }
        }
    }
}
