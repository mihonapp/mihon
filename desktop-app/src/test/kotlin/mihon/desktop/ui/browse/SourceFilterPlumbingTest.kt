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
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking
import mihon.desktop.extension.DesktopSourceManager
import mihon.desktop.extension.WindowsExtensionProcessManager
import mihon.desktop.extension.builtin.BundledMangaDexSource
import mihon.extension.model.SourceDescriptor
import mihon.extension.source.model.Filter
import mihon.extension.source.model.FilterList
import mihon.extension.source.model.MangasPage
import mihon.extension.source.model.SManga
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class SourceFilterPlumbingTest {

    private val testSource = SourceDescriptor(
        id = 4242L,
        name = "Filter Test Source",
        lang = "en",
        className = "ext.test.FilterTestSource",
        supportsLatest = true,
    )

    private class FilterCapturingProcessManager(
        workingDirectory: File,
        private val filterList: FilterList,
    ) : WindowsExtensionProcessManager(workingDirectory) {

        var lastSearchPage: Int? = null
        var lastSearchQuery: String? = null
        var lastSearchFilters: FilterList? = null

        override suspend fun getFilterList(sourceId: Long): FilterList = filterList

        override suspend fun searchManga(
            sourceId: Long,
            page: Int,
            query: String,
            filters: FilterList,
        ): MangasPage {
            lastSearchPage = page
            lastSearchQuery = query
            lastSearchFilters = filters
            return MangasPage(
                mangas = listOf(SManga(url = "/manga/$page", title = "Result $query page $page")),
                hasNextPage = true,
            )
        }
    }

    private fun allFilterTypes(): FilterList = FilterList(
        Filter.Header("Header"),
        Filter.Separator("Separator"),
        Filter.Text("Author", "Oda"),
        Filter.CheckBox("Completed", true),
        Filter.Select("Status", arrayOf("Any", "Ongoing", "Completed"), 2),
        Filter.TriState("Genre", Filter.TriState.STATE_EXCLUDE),
        Filter.Group(
            "Group",
            listOf(
                Filter.CheckBox("Action", true),
                Filter.TriState("Romance", Filter.TriState.STATE_INCLUDE),
            ),
        ),
        Filter.Sort("Sort", arrayOf("Title", "Date"), Filter.Sort.Selection(1, true)),
    )

    @Test
    fun `manager maps extension filter list and passes filters to search`(@TempDir tempDir: Path) {
        val expectedFilters = allFilterTypes()
        val processManager = FilterCapturingProcessManager(tempDir.toFile(), expectedFilters)
        val manager = DesktopSourceManager(installer = null, processManager = processManager)

        try {
            val loaded = manager.getFilterList(4242L)

            loaded.filters shouldHaveSize 8
            loaded.filters[2].shouldBeInstanceOf<Filter.Text>().state shouldBe "Oda"
            loaded.filters[3].shouldBeInstanceOf<Filter.CheckBox>().state shouldBe true
            loaded.filters[4].shouldBeInstanceOf<Filter.Select<*>>().state shouldBe 2
            loaded.filters[5].shouldBeInstanceOf<Filter.TriState>().state shouldBe Filter.TriState.STATE_EXCLUDE
            loaded.filters[6].shouldBeInstanceOf<Filter.Group<*>>().state[0]
                .shouldBeInstanceOf<Filter.CheckBox>()
                .state shouldBe true
            loaded.filters[7].shouldBeInstanceOf<Filter.Sort>().state shouldBe Filter.Sort.Selection(1, true)

            val page = runBlocking { manager.searchManga(4242L, 2, "one piece", loaded) }

            processManager.lastSearchPage shouldBe 2
            processManager.lastSearchQuery shouldBe "one piece"
            processManager.lastSearchFilters shouldBe loaded
            page.hasNextPage shouldBe true
            page.mangas.single().title shouldBe "Result one piece page 2"
        } finally {
            manager.close()
        }
    }

    @Test
    fun `builtin MangaDex filters remain available`() {
        val manager = DesktopSourceManager(processManager = null)

        try {
            val filters = manager.getFilterList(BundledMangaDexSource.MANGADEX_SOURCE_ID)

            filters.filters shouldHaveSize 4
            filters.filters[0].shouldBeInstanceOf<BundledMangaDexSource.SortFilter>().state shouldBe
                Filter.Sort.Selection(0, false)
            filters.filters[1].shouldBeInstanceOf<BundledMangaDexSource.ContentRatingGroup>().state shouldHaveSize 4
            filters.filters[2].shouldBeInstanceOf<BundledMangaDexSource.StatusGroup>().state shouldHaveSize 4
            filters.filters[3].shouldBeInstanceOf<BundledMangaDexSource.OriginalLanguageFilter>().state shouldBe 0
        } finally {
            manager.close()
        }
    }

    @Test
    fun `active filter count reflects every filter type`() {
        val state = BrowseSourceUiState(
            source = testSource,
            filterList = allFilterTypes(),
        )

        // Text, CheckBox, Select, TriState, two Group children and Sort.
        state.activeFilterCount shouldBe 7
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `source filter dialog displays filters and applies selected state`() = runComposeUiTest {
        val filters = FilterList(
            Filter.CheckBox("Completed", false),
            Filter.Text("Author", ""),
        )
        var applied: FilterList? = null

        setContent {
            Box(modifier = Modifier.requiredSize(1000.dp, 900.dp)) {
                BrowseSourceScreen(
                    state = BrowseSourceUiState(
                        source = testSource,
                        filterList = filters,
                        isFilterDialogOpen = true,
                    ),
                    onBack = {},
                    onModeChange = {},
                    onQueryChange = {},
                    onSearch = {},
                    onPageChange = {},
                    onMangaSelected = {},
                    onApplyFilters = { applied = it },
                )
            }
        }

        onNodeWithTag("source-filter-dialog").assertIsDisplayed()
        onNodeWithText("Completed").assertIsDisplayed()

        onNodeWithTag("filter-checkbox-Completed").performClick()
        onNodeWithTag("filter-apply-btn").performClick()

        val result = applied
        result shouldBe filters
        result!!.filters shouldHaveSize 2
        result.filters[0].shouldBeInstanceOf<Filter.CheckBox>().state shouldBe true
    }
}
