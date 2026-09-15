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

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `site block is explained without promising a verification retry`() = runComposeUiTest {
        setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                mihon.desktop.i18n.LocalStrings provides mihon.desktop.i18n.SimplifiedChineseStrings,
            ) {
                BrowseSourceScreen(
                    state = BrowseSourceUiState(
                        source = testSource,
                        errorMessage = "HTTP error 403",
                        networkFailure = mihon.extension.ipc.NetworkFailure(
                            mihon.extension.ipc.NetworkFailureKind.SITE_BLOCKED,
                            403,
                            "vapi.ezmanga.org",
                        ),
                    ),
                    onBack = {},
                    onModeChange = {},
                    onQueryChange = {},
                    onSearch = {},
                    onPageChange = {},
                    onMangaSelected = {},
                    onOpenWebPage = {},
                )
            }
        }
        onNodeWithText("网站已封锁访问。", substring = true).assertIsDisplayed()
        onNodeWithText("vapi.ezmanga.org · HTTP 403", substring = true).assertIsDisplayed()
        onNodeWithTag("source-open-webpage").assertIsDisplayed()
    }

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

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `local source empty state shows import action`() = runComposeUiTest {
        var importClicked = false

        setContent {
            Box(modifier = Modifier.requiredSize(800.dp, 600.dp)) {
                BrowseSourceScreen(
                    state = BrowseSourceUiState(
                        source = SourceDescriptor(
                            id = 0L,
                            name = "Local source",
                            lang = "other",
                            className = "mihon.desktop.extension.builtin.BundledLocalSource",
                            supportsLatest = true,
                        ),
                    ),
                    onBack = {},
                    onModeChange = {},
                    onQueryChange = {},
                    onSearch = {},
                    onPageChange = {},
                    onMangaSelected = {},
                    onImportLocal = { importClicked = true },
                )
            }
        }

        onNodeWithTag("local-source-import-btn").assertIsDisplayed()
        onNodeWithTag("local-source-import-btn").performClick()
        importClicked shouldBe true
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `local manga cards display chapter count`() = runComposeUiTest {
        val sampleManga = SManga(
            url = "local:alpha",
            title = "Alpha",
        )

        setContent {
            Box(modifier = Modifier.requiredSize(800.dp, 600.dp)) {
                BrowseSourceScreen(
                    state = BrowseSourceUiState(
                        source = SourceDescriptor(
                            id = 0L,
                            name = "Local source",
                            lang = "other",
                            className = "mihon.desktop.extension.builtin.BundledLocalSource",
                            supportsLatest = true,
                        ),
                        mangas = listOf(sampleManga),
                        chapterCounts = mapOf(sampleManga.url to 3L),
                    ),
                    onBack = {},
                    onModeChange = {},
                    onQueryChange = {},
                    onSearch = {},
                    onPageChange = {},
                    onMangaSelected = {},
                )
            }
        }

        onNodeWithTag("manga-chapter-count-${sampleManga.url}", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("3 chapters").assertIsDisplayed()
    }
}
