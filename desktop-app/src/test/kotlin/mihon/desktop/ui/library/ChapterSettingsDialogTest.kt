package mihon.desktop.ui.library

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class ChapterSettingsDialogTest {
    @Test
    fun `renders settings controls and dispatches callbacks`() = runComposeUiTest {
        var displayMode: ChapterDisplayMode? = null
        var sortSelection: Pair<ChapterSortMode, Boolean>? = null
        var showMissing: Boolean? = null
        var excluded: Set<String>? = null
        var applyToExisting: Boolean? = null
        var reset = false
        var dismissed = false

        setContent {
            MaterialTheme {
                ChapterSettingsDialog(
                    settings = ChapterSettings(
                        displayMode = ChapterDisplayMode.Name,
                        sortMode = ChapterSortMode.SourceOrder,
                        sortAscending = false,
                        showMissingChapters = true,
                    ),
                    availableScanlators = setOf("Group A", "Group B"),
                    onDismissRequest = { dismissed = true },
                    onDisplayModeChange = { displayMode = it },
                    onSortModeChange = { mode, ascending -> sortSelection = mode to ascending },
                    onShowMissingChaptersChange = { showMissing = it },
                    onExcludedScanlatorsChange = { excluded = it },
                    onSetAsDefault = { applyToExisting = it },
                    onResetToDefault = { reset = true },
                )
            }
        }

        onNodeWithTag("chapter-settings-dialog").assertExists()
        onNodeWithTag("chapter-settings-display-Number").performClick()
        displayMode shouldBe ChapterDisplayMode.Number

        onNodeWithTag("chapter-settings-missing").performClick()
        showMissing shouldBe false

        onNodeWithTag("chapter-settings-sort-ChapterNumber").performClick()
        sortSelection?.first shouldBe ChapterSortMode.ChapterNumber
        onNodeWithTag("chapter-settings-sort-ascending").performScrollTo().performClick()
        sortSelection?.second shouldBe true

        onNodeWithTag("chapter-settings-scanlators").performScrollTo().performClick()
        onNodeWithTag("scanlator-filter-dialog").assertExists()
        onNodeWithTag("scanlator-filter-option-Group B").performClick()
        onNodeWithTag("scanlator-filter-confirm").performClick()
        excluded shouldBe setOf("Group B")

        onNodeWithTag("chapter-settings-set-default").performScrollTo().performClick()
        onNodeWithTag("chapter-settings-set-default-dialog").assertExists()
        onNodeWithTag("chapter-settings-apply-existing").performClick()
        onNodeWithTag("chapter-settings-set-default-confirm").performClick()
        applyToExisting shouldBe true

        onNodeWithTag("chapter-settings-reset").performScrollTo().performClick()
        reset shouldBe true

        onNodeWithTag("chapter-settings-done").performClick()
        dismissed shouldBe true
    }
}
