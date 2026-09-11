package mihon.desktop.ui.upcoming

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kotest.matchers.shouldBe
import mihon.desktop.category.DesktopCategory
import mihon.desktop.ui.library.TriStateFilter
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

class UpcomingScreenTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `calendar navigation and day selection update the visible entries`() {
        val previousLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            val sep20 = LocalDate.of(2025, 9, 20)
            val sep21 = LocalDate.of(2025, 9, 21)
            val initialState = UpcomingUiState(
                loading = false,
                selectedMonth = YearMonth.of(2025, 9),
                today = LocalDate.of(2025, 9, 15),
                entriesByDate = mapOf(
                    sep20 to listOf(entry(11L, 1L, "Alpha", "Chapter 1", sep20, "Source Seven")),
                    sep21 to listOf(entry(12L, 2L, "Beta", "Chapter 2", sep21, "Source Eight")),
                ),
            )

            runComposeUiTest {
                setContent {
                    Box(modifier = Modifier.requiredSize(1100.dp, 760.dp)) {
                        var state by remember { mutableStateOf(initialState) }
                        UpcomingScreen(
                            state = state,
                            onPreviousMonth = {
                                state =
                                    state.copy(selectedMonth = state.selectedMonth.minusMonths(1), selectedDate = null)
                            },
                            onNextMonth = {
                                state =
                                    state.copy(selectedMonth = state.selectedMonth.plusMonths(1), selectedDate = null)
                            },
                            onSelectDate = { state = state.copy(selectedDate = it) },
                        )
                    }
                }

                onNodeWithTag(UPCOMING_SCREEN_TEST_TAG).assertIsDisplayed()
                onNodeWithTag(UPCOMING_MONTH_HEADER_TEST_TAG).assertTextEquals("September 2025")
                onNodeWithText("Alpha").assertIsDisplayed()
                onNodeWithText("Beta").assertIsDisplayed()

                onNodeWithTag(UPCOMING_NEXT_MONTH_TEST_TAG).performClick()
                onNodeWithTag(UPCOMING_MONTH_HEADER_TEST_TAG).assertTextEquals("October 2025")

                onNodeWithTag(UPCOMING_PREVIOUS_MONTH_TEST_TAG).performClick()
                onNodeWithTag(UPCOMING_MONTH_HEADER_TEST_TAG).assertTextEquals("September 2025")

                onNodeWithTag(UPCOMING_DAY_TEST_TAG_PREFIX + sep20).performClick()
                onNodeWithTag(UPCOMING_ITEM_TEST_TAG_PREFIX + 11L).assertIsDisplayed()
                onNodeWithTag(UPCOMING_ITEM_TEST_TAG_PREFIX + 12L).assertDoesNotExist()
                onNodeWithText("Source Seven").assertIsDisplayed()

                onNodeWithTag(UPCOMING_SHOW_MONTH_TEST_TAG).performClick()
                onNodeWithTag(UPCOMING_ITEM_TEST_TAG_PREFIX + 11L).assertIsDisplayed()
                onNodeWithTag(UPCOMING_ITEM_TEST_TAG_PREFIX + 12L).assertIsDisplayed()
            }
        } finally {
            Locale.setDefault(previousLocale)
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `renders empty state when there are no future chapters`() {
        runComposeUiTest {
            setContent {
                Box(modifier = Modifier.requiredSize(1000.dp, 700.dp)) {
                    UpcomingScreen(
                        state = UpcomingUiState(
                            loading = false,
                            selectedMonth = YearMonth.of(2025, 9),
                            today = LocalDate.of(2025, 9, 15),
                        ),
                    )
                }
            }

            onNodeWithTag(UPCOMING_EMPTY_STATE_TEST_TAG).assertIsDisplayed()
            onNodeWithText("No upcoming chapters").assertIsDisplayed()
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `clicking an entry opens the manga callback`() {
        var openedMangaId: Long? = null
        val date = LocalDate.of(2025, 9, 20)

        runComposeUiTest {
            setContent {
                Box(modifier = Modifier.requiredSize(1000.dp, 700.dp)) {
                    UpcomingScreen(
                        state = UpcomingUiState(
                            loading = false,
                            selectedMonth = YearMonth.of(2025, 9),
                            today = LocalDate.of(2025, 9, 15),
                            entriesByDate = mapOf(
                                date to listOf(entry(21L, 42L, "Gamma", "Chapter 7", date, "Source Seven")),
                            ),
                        ),
                        onOpenManga = { openedMangaId = it },
                    )
                }
            }

            onNodeWithTag(UPCOMING_ITEM_TEST_TAG_PREFIX + 21L).performClick()
            openedMangaId shouldBe 42L
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `filter dialog cycles a category through tri-state values`() {
        val category = DesktopCategory(id = 10L, name = "Action", order = 0L)

        runComposeUiTest {
            setContent {
                Box(modifier = Modifier.requiredSize(1000.dp, 700.dp)) {
                    var state by remember {
                        mutableStateOf(
                            UpcomingUiState(
                                loading = false,
                                selectedMonth = YearMonth.of(2025, 9),
                                today = LocalDate.of(2025, 9, 15),
                                categories = listOf(category),
                                isFilterDialogOpen = true,
                            ),
                        )
                    }
                    UpcomingScreen(
                        state = state,
                        onCycleCategory = { categoryId ->
                            val current = state.categoryFilters[categoryId] ?: TriStateFilter.Disabled
                            val next = current.next()
                            val updated = state.categoryFilters.toMutableMap()
                            if (next == TriStateFilter.Disabled) {
                                updated.remove(categoryId)
                            } else {
                                updated[categoryId] = next
                            }
                            state = state.copy(categoryFilters = updated)
                        },
                        onDismissFilter = { state = state.copy(isFilterDialogOpen = false) },
                    )
                }
            }

            onNodeWithTag(UPCOMING_FILTER_DIALOG_TEST_TAG).assertIsDisplayed()
            onNodeWithTag(UPCOMING_FILTER_CATEGORY_TEST_TAG_PREFIX + 10L).performClick()
            onNodeWithText("✓").assertIsDisplayed()
        }
    }

    private fun entry(
        chapterId: Long,
        mangaId: Long,
        mangaTitle: String,
        chapterName: String,
        date: LocalDate,
        sourceName: String,
    ) = UpcomingChapterEntry(
        mangaId = mangaId,
        mangaTitle = mangaTitle,
        mangaThumbnailUrl = null,
        sourceId = 7L,
        sourceName = sourceName,
        chapterId = chapterId,
        chapterName = chapterName,
        chapterNumber = chapterId.toDouble(),
        dateUpload = date.atTime(12, 0).atZone(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(),
        categoryIds = emptySet(),
    )
}
