package mihon.desktop.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import mihon.desktop.category.DesktopCategory
import mihon.desktop.category.SYSTEM_ALL_CATEGORY
import mihon.desktop.library.model.LibraryManga
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LibraryCategoryFilterTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `library screen renders category chips and notifies category selection`() = runComposeUiTest {
        var selectedCatId: Long? = null
        var manageCategoriesClicked = false

        val categories = listOf(
            SYSTEM_ALL_CATEGORY,
            DesktopCategory(id = 10L, name = "Shonen", order = 1),
            DesktopCategory(id = 20L, name = "Seinen", order = 2),
        )

        val state = LibraryUiState(
            loading = false,
            items = listOf(
                LibraryManga(
                    id = 1L,
                    sourceId = 1L,
                    url = "/1",
                    title = "Bleach",
                    thumbnailUrl = null,
                    chapterCount = 10,
                    unreadCount = 5,
                ),
            ),
            categories = categories,
            selectedCategoryId = SYSTEM_ALL_CATEGORY.id,
        )

        setContent {
            Box(modifier = Modifier.requiredSize(800.dp, 600.dp)) {
                LibraryScreen(
                    state = state,
                    onQueryChange = {},
                    onMangaSelected = {},
                    onImportBackup = {},
                    onImportLocal = {},
                    onCategorySelected = { selectedCatId = it },
                    onManageCategories = { manageCategoriesClicked = true },
                )
            }
        }

        onNodeWithTag("library-categories-row").assertExists()
        onNodeWithTag("library-category-chip--1").assertExists()
        onNodeWithTag("library-category-chip-10").assertExists()
        onNodeWithTag("library-category-chip-20").assertExists()

        onNodeWithTag("library-category-chip-10").performClick()
        assertEquals(10L, selectedCatId)

        onNodeWithTag("library-manage-categories-button").performClick()
        assertEquals(true, manageCategoriesClicked)
    }
}
