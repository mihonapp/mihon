package mihon.desktop.ui.browse

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kotest.matchers.shouldBe
import mihon.extension.model.SourceDescriptor
import mihon.extension.source.model.SManga
import org.junit.jupiter.api.Test

class BrowseSourceScreenTest {

    private val testSource = SourceDescriptor(
        id = 123L,
        name = "Test Manga Source",
        lang = "en",
        className = "ext.test.TestSource",
        supportsLatest = true,
    )

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `displays manga grid and triggers page and selection callbacks`() = runComposeUiTest {
        var selectedManga: SManga? = null
        var changedPage: Int? = null
        var changedMode: SourceListingMode? = null

        val sampleManga = SManga(
            url = "/manga/one",
            title = "Awesome Manga",
        )

        setContent {
            Box(modifier = Modifier.requiredSize(800.dp, 600.dp)) {
                BrowseSourceScreen(
                    state = BrowseSourceUiState(
                        source = testSource,
                        mangas = listOf(sampleManga),
                        inLibraryUrls = setOf("/manga/one"),
                        hasNextPage = true,
                        page = 1,
                    ),
                    onBack = {},
                    onModeChange = { changedMode = it },
                    onQueryChange = {},
                    onSearch = {},
                    onPageChange = { changedPage = it },
                    onMangaSelected = { selectedManga = it },
                )
            }
        }

        onNodeWithText("Awesome Manga").assertIsDisplayed()
        onNodeWithText("IN LIBRARY").assertIsDisplayed()

        // Test card click
        onNodeWithTag("manga-card-/manga/one").performClick()
        selectedManga shouldBe sampleManga

        // Test next page click
        onNodeWithTag("next-page-btn").performClick()
        changedPage shouldBe 2

        // Test mode switch click
        onNodeWithTag("mode-latest-chip").performClick()
        changedMode shouldBe SourceListingMode.Latest
    }
}