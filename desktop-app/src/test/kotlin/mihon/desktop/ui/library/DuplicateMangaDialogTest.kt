package mihon.desktop.ui.library

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class DuplicateMangaDialogTest {
    @Test
    fun `renders candidate details and dispatches open migrate add anyway and cancel`() = runComposeUiTest {
        var opened: Long? = null
        var migrated: Long? = null
        var addAnyway = false
        var dismissed = false

        setContent {
            MaterialTheme {
                DuplicateMangaDialog(
                    state = DuplicateMangaDialogState(
                        groupKey = "onepiece|1,2",
                        target = DuplicateMangaCandidate(
                            id = 1L,
                            title = "One Piece",
                            sourceId = 100L,
                            thumbnailUrl = null,
                            chapterCount = 10L,
                        ),
                        candidates = listOf(
                            DuplicateMangaCandidate(
                                id = 2L,
                                title = "One Piece",
                                sourceId = 200L,
                                thumbnailUrl = null,
                                chapterCount = 20L,
                            ),
                        ),
                    ),
                    sourceNameFor = { sourceId -> if (sourceId == 200L) "Source B" else "Source A" },
                    onDismissRequest = { dismissed = true },
                    onAddAnyway = { addAnyway = true },
                    onOpenManga = { opened = it },
                    onMigrate = { migrated = it },
                )
            }
        }

        onNodeWithTag("duplicate-manga-dialog").assertExists()
        onNodeWithText("Possible duplicates").assertExists()
        onNodeWithTag("duplicate-candidate-2").assertExists()
        onNodeWithText("Source B").assertExists()
        onNodeWithText("20 chapters").assertExists()

        onNodeWithTag("duplicate-open-2").performClick()
        opened shouldBe 2L
        onNodeWithTag("duplicate-migrate-2").performClick()
        migrated shouldBe 2L
        onNodeWithTag("duplicate-add-anyway").performClick()
        addAnyway shouldBe true
        onNodeWithTag("duplicate-cancel").performClick()
        dismissed shouldBe true
    }
}
