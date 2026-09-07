package mihon.desktop.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mihon.desktop.i18n.EnglishStrings
import mihon.desktop.library.model.CategoryRecord
import mihon.desktop.library.model.ChapterRecord
import mihon.desktop.library.model.HistoryRecord
import mihon.desktop.library.model.HistoryWithDetails
import mihon.desktop.library.model.ImportReport
import mihon.desktop.library.model.LibraryChapter
import mihon.desktop.library.model.LibraryManga
import mihon.desktop.library.model.MangaDetails
import mihon.desktop.library.model.MangaRecord
import mihon.desktop.library.model.PreferenceSnapshotRecord
import mihon.desktop.library.model.SourcePreferenceSnapshotRecord
import mihon.desktop.library.model.SourceRecord
import mihon.desktop.library.model.TrackingRecord
import mihon.desktop.library.repository.LibraryRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class LibraryFilterAndSortTest {
    private val parentJob = SupervisorJob()
    private val scope = CoroutineScope(parentJob + Dispatchers.Default)

    @AfterEach
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `tri-state filter cycle transitions correctly`() {
        TriStateFilter.Disabled.next() shouldBe TriStateFilter.Include
        TriStateFilter.Include.next() shouldBe TriStateFilter.Exclude
        TriStateFilter.Exclude.next() shouldBe TriStateFilter.Disabled
    }

    @Test
    fun `sorting by alphabetical orders titles case-insensitively`() = runBlocking {
        val rows = MutableStateFlow(
            listOf(
                manga(1, "Zebra", unread = 0, chapters = 10),
                manga(2, "apple", unread = 5, chapters = 20),
                manga(3, "Banana", unread = 2, chapters = 5),
            ),
        )
        val presenter = LibraryPresenter(FilterTestFakeRepository { rows }, scope)
        presenter.awaitState { !it.loading && it.items.size == 3 }

        // Alphabetical Ascending
        presenter.setSortState(LibrarySortState(mode = LibrarySortMode.Alphabetical, ascending = true))
        presenter.awaitState { it.sortState.mode == LibrarySortMode.Alphabetical && it.sortState.ascending }
            .items.map { it.title }.shouldContainExactly("apple", "Banana", "Zebra")

        // Alphabetical Descending
        presenter.setSortState(LibrarySortState(mode = LibrarySortMode.Alphabetical, ascending = false))
        presenter.awaitState { it.sortState.mode == LibrarySortMode.Alphabetical && !it.sortState.ascending }
            .items.map { it.title }.shouldContainExactly("Zebra", "Banana", "apple")

        presenter.close()
    }

    @Test
    fun `sorting by unread count orders items correctly`() = runBlocking {
        val rows = MutableStateFlow(
            listOf(
                manga(1, "A", unread = 10, chapters = 20),
                manga(2, "B", unread = 1, chapters = 10),
                manga(3, "C", unread = 5, chapters = 15),
            ),
        )
        val presenter = LibraryPresenter(FilterTestFakeRepository { rows }, scope)
        presenter.awaitState { !it.loading && it.items.size == 3 }

        // Unread Ascending
        presenter.setSortState(LibrarySortState(mode = LibrarySortMode.UnreadCount, ascending = true))
        presenter.awaitState { it.sortState.mode == LibrarySortMode.UnreadCount && it.sortState.ascending }
            .items.map { it.unreadCount }.shouldContainExactly(1, 5, 10)

        // Unread Descending
        presenter.setSortState(LibrarySortState(mode = LibrarySortMode.UnreadCount, ascending = false))
        presenter.awaitState { it.sortState.mode == LibrarySortMode.UnreadCount && !it.sortState.ascending }
            .items.map { it.unreadCount }.shouldContainExactly(10, 5, 1)

        presenter.close()
    }

    @Test
    fun `sorting by total chapters orders items correctly`() = runBlocking {
        val rows = MutableStateFlow(
            listOf(
                manga(1, "A", unread = 0, chapters = 100),
                manga(2, "B", unread = 0, chapters = 10),
                manga(3, "C", unread = 0, chapters = 50),
            ),
        )
        val presenter = LibraryPresenter(FilterTestFakeRepository { rows }, scope)
        presenter.awaitState { !it.loading && it.items.size == 3 }

        presenter.setSortState(LibrarySortState(mode = LibrarySortMode.TotalChapters, ascending = true))
        presenter.awaitState { it.sortState.mode == LibrarySortMode.TotalChapters && it.sortState.ascending }
            .items.map { it.chapterCount }.shouldContainExactly(10, 50, 100)

        presenter.close()
    }

    @Test
    fun `unread filter include and exclude filters rows properly`() = runBlocking {
        val rows = MutableStateFlow(
            listOf(
                manga(1, "Unread Manga", unread = 3, chapters = 10),
                manga(2, "Read Manga", unread = 0, chapters = 10),
            ),
        )
        val presenter = LibraryPresenter(FilterTestFakeRepository { rows }, scope)
        presenter.awaitState { !it.loading && it.items.size == 2 }

        // Include unread
        presenter.setFilterState(LibraryFilterState(unread = TriStateFilter.Include))
        presenter.awaitState { it.filterState.unread == TriStateFilter.Include && it.items.size == 1 }
            .items.single().id shouldBe 1L

        // Exclude unread (only read manga)
        presenter.setFilterState(LibraryFilterState(unread = TriStateFilter.Exclude))
        presenter.awaitState { it.filterState.unread == TriStateFilter.Exclude && it.items.size == 1 }
            .items.single().id shouldBe 2L

        // Reset
        presenter.setFilterState(LibraryFilterState())
        presenter.awaitState { !it.filterState.hasActiveFilters && it.items.size == 2 }

        presenter.close()
    }

    @Test
    fun `started filter filters partially read manga`() = runBlocking {
        val rows = MutableStateFlow(
            listOf(
                manga(1, "Started", unread = 3, chapters = 10), // chapterCount(10) > unreadCount(3) > 0
                manga(2, "Unstarted", unread = 10, chapters = 10), // chapterCount == unreadCount
                manga(3, "Completed", unread = 0, chapters = 10), // unreadCount == 0
            ),
        )
        val presenter = LibraryPresenter(FilterTestFakeRepository { rows }, scope)
        presenter.awaitState { !it.loading && it.items.size == 3 }

        presenter.setFilterState(LibraryFilterState(started = TriStateFilter.Include))
        presenter.awaitState { it.filterState.started == TriStateFilter.Include && it.items.size == 1 }
            .items.single().id shouldBe 1L

        presenter.setFilterState(LibraryFilterState(started = TriStateFilter.Exclude))
        presenter.awaitState { it.filterState.started == TriStateFilter.Exclude && it.items.size == 2 }
            .items.map { it.id }.shouldContainExactly(2L, 3L)

        presenter.close()
    }

    @Test
    fun `completed filter filters fully read manga`() = runBlocking {
        val rows = MutableStateFlow(
            listOf(
                manga(1, "In Progress", unread = 2, chapters = 10),
                manga(2, "Fully Read", unread = 0, chapters = 10),
            ),
        )
        val presenter = LibraryPresenter(FilterTestFakeRepository { rows }, scope)
        presenter.awaitState { !it.loading && it.items.size == 2 }

        presenter.setFilterState(LibraryFilterState(completed = TriStateFilter.Include))
        presenter.awaitState { it.filterState.completed == TriStateFilter.Include && it.items.size == 1 }
            .items.single().id shouldBe 2L

        presenter.close()
    }

    @Test
    fun `display mode and grid size state mutations reflect in state`() = runBlocking {
        val rows = MutableStateFlow(listOf(manga(1, "Sample", unread = 0, chapters = 1)))
        val presenter = LibraryPresenter(FilterTestFakeRepository { rows }, scope)
        presenter.awaitState { !it.loading }

        presenter.setDisplayMode(LibraryDisplayMode.List)
        presenter.awaitState { it.displayMode == LibraryDisplayMode.List }

        presenter.setDisplayMode(LibraryDisplayMode.CompactGrid)
        presenter.awaitState { it.displayMode == LibraryDisplayMode.CompactGrid }

        presenter.setDisplayMode(LibraryDisplayMode.CoverOnly)
        presenter.awaitState { it.displayMode == LibraryDisplayMode.CoverOnly }

        presenter.setGridSize(240f)
        presenter.awaitState { it.gridSize == 240f }

        presenter.close()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `filter and sort dialog cycles chips and changes sort`() = runComposeUiTest {
        var currentFilters = LibraryFilterState()
        var currentSort = LibrarySortState()
        var dismissed = false

        setContent {
            MaterialTheme {
                Box(modifier = Modifier.requiredSize(600.dp, 600.dp)) {
                    LibraryFilterDialog(
                        filterState = currentFilters,
                        sortState = currentSort,
                        onFilterChange = { currentFilters = it },
                        onSortChange = { currentSort = it },
                        onDismiss = { dismissed = true },
                    )
                }
            }
        }

        // Verify Filter tab chips exist
        onNodeWithTag("filter-unread").assertExists()
        onNodeWithTag("filter-unread").performClick()
        currentFilters.unread shouldBe TriStateFilter.Include

        // Switch to Sort tab
        onNodeWithTag("filter-tab-sort").performClick()
        onNodeWithTag("sort-option-TotalChapters").assertExists()
        onNodeWithTag("sort-option-TotalChapters").performClick()
        currentSort.mode shouldBe LibrarySortMode.TotalChapters

        onNodeWithTag("sort-descending").performClick()
        currentSort.ascending shouldBe false

        // Close dialog
        onNodeWithTag("filter-dialog-done").performClick()
        dismissed shouldBe true
    }

    private suspend fun LibraryPresenter.awaitState(predicate: (LibraryUiState) -> Boolean): LibraryUiState =
        withTimeout(5_000) { state.first(predicate) }

    private fun manga(
        id: Long,
        title: String,
        unread: Int,
        chapters: Int,
    ) = LibraryManga(
        id = id,
        sourceId = 100 + id,
        url = "/$id",
        title = title,
        thumbnailUrl = null,
        chapterCount = chapters.toLong(),
        unreadCount = unread.toLong(),
        author = null,
    )
}

private class FilterTestFakeRepository(
    private val libraryFlow: () -> Flow<List<LibraryManga>>,
) : LibraryRepository {
    override fun observeLibrary(categoryId: Long?): Flow<List<LibraryManga>> = libraryFlow()
    override fun observeManga(id: Long): Flow<MangaDetails?> = flowOf(null)
    override fun observeChapters(mangaId: Long): Flow<List<LibraryChapter>> = flowOf(emptyList())
    override fun observeCategories(): Flow<List<CategoryRecord>> = flowOf(emptyList())
    override fun observeHistory(query: String): Flow<List<HistoryWithDetails>> = flowOf(emptyList())
    override fun observeTracking(mangaId: Long): Flow<List<TrackingRecord>> = flowOf(emptyList())
    override fun librarySnapshot(categoryId: Long?): List<LibraryManga> = emptyList()
    override fun mangaSnapshot(id: Long): MangaDetails? = null
    override fun chapterSnapshot(mangaId: Long): List<LibraryChapter> = emptyList()
    override fun categoriesSnapshot(): List<CategoryRecord> = emptyList()
    override fun historySnapshot(query: String): List<HistoryWithDetails> = emptyList()
    override fun trackingSnapshot(mangaId: Long): List<TrackingRecord> = emptyList()
    override fun latestImportReport(): ImportReport? = null
    override fun allMangaSnapshot(): List<MangaRecord> = emptyList()
    override fun allChaptersSnapshot(): List<ChapterRecord> = emptyList()
    override fun allCategoriesSnapshot(): List<CategoryRecord> = emptyList()
    override fun mangaCategoryLinksSnapshot(): Map<Long, List<Long>> = emptyMap()
    override fun allHistorySnapshot(): List<HistoryRecord> = emptyList()
    override fun allTrackingSnapshot(): List<TrackingRecord> = emptyList()
    override fun allSourcesSnapshot(): List<SourceRecord> = emptyList()
    override fun allPreferenceSnapshots(): List<PreferenceSnapshotRecord> = emptyList()
    override fun allSourcePreferenceSnapshots(): List<SourcePreferenceSnapshotRecord> = emptyList()
    override fun checkIntegrity(): List<String> = listOf("ok")
}
