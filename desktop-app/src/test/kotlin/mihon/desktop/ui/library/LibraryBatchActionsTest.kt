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
import mihon.desktop.library.model.CategoryRecord
import mihon.desktop.library.model.ChapterRecord
import mihon.desktop.library.model.HistoryRecord
import mihon.desktop.library.model.HistoryWithDetails
import mihon.desktop.library.model.ImportReport
import mihon.desktop.library.model.ImportReportItemRecord
import mihon.desktop.library.model.ImportReportRecord
import mihon.desktop.library.model.LibraryChapter
import mihon.desktop.library.model.LibraryManga
import mihon.desktop.library.model.LocalChapterRecord
import mihon.desktop.library.model.LocalMangaRecord
import mihon.desktop.library.model.MangaDetails
import mihon.desktop.library.model.MangaRecord
import mihon.desktop.library.model.PreferenceSnapshotRecord
import mihon.desktop.library.model.SourcePreferenceSnapshotRecord
import mihon.desktop.library.model.SourceRecord
import mihon.desktop.library.model.TrackingRecord
import mihon.desktop.library.repository.LibraryMutationPort
import mihon.desktop.library.repository.LibraryRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class LibraryBatchActionsTest {
    private val parentJob = SupervisorJob()
    private val scope = CoroutineScope(parentJob + Dispatchers.Default)

    @AfterEach
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `selection operations toggle ids select all and clear properly`() = runBlocking {
        val rows = MutableStateFlow(
            listOf(
                manga(1, "Manga 1"),
                manga(2, "Manga 2"),
                manga(3, "Manga 3"),
            ),
        )
        val presenter = LibraryPresenter(BatchTestFakeRepository(libraryRows = rows), scope)
        presenter.awaitState { !it.loading && it.items.size == 3 }

        // Start selection mode
        presenter.toggleSelectionMode(true)
        presenter.awaitState { it.selectionState.isSelectionMode }
            .selectionState.selectedMangaIds shouldBe emptySet()

        // Toggle manga 1
        presenter.toggleMangaSelection(1L)
        presenter.awaitState { it.selectionState.selectedMangaIds.contains(1L) }
            .selectionState.selectedMangaIds shouldContainExactly setOf(1L)

        // Toggle manga 2
        presenter.toggleMangaSelection(2L)
        presenter.awaitState { it.selectionState.selectedMangaIds.size == 2 }
            .selectionState.selectedMangaIds shouldContainExactly setOf(1L, 2L)

        // Toggle manga 1 off
        presenter.toggleMangaSelection(1L)
        presenter.awaitState { it.selectionState.selectedMangaIds.size == 1 }
            .selectionState.selectedMangaIds shouldContainExactly setOf(2L)

        // Select All
        presenter.selectAll()
        presenter.awaitState { it.selectionState.selectedMangaIds.size == 3 }
            .selectionState.selectedMangaIds shouldContainExactly setOf(1L, 2L, 3L)

        // Clear Selection
        presenter.clearSelection()
        presenter.awaitState { !it.selectionState.isSelectionMode && it.selectionState.selectedMangaIds.isEmpty() }

        presenter.close()
    }

    @Test
    fun `batchSetCategories updates categories for selected manga and clears selection`() = runBlocking {
        val repo = BatchTestFakeRepository(
            libraryRows = MutableStateFlow(listOf(manga(1, "Manga 1"), manga(2, "Manga 2"))),
        )
        val presenter = LibraryPresenter(repo, scope)
        presenter.awaitState { !it.loading && it.items.size == 2 }

        presenter.toggleMangaSelection(1L)
        presenter.toggleMangaSelection(2L)
        presenter.awaitState { it.selectionState.selectedMangaIds.size == 2 }

        presenter.batchSetCategories(listOf(10L, 20L))
        presenter.awaitState { !it.selectionState.isSelectionMode && it.selectionState.selectedMangaIds.isEmpty() }

        repo.mangaCategoryMap[1L] shouldContainExactly listOf(10L, 20L)
        repo.mangaCategoryMap[2L] shouldContainExactly listOf(10L, 20L)

        presenter.close()
    }

    @Test
    fun `batchMarkRead updates all chapters of selected manga`() = runBlocking {
        val repo = BatchTestFakeRepository(
            libraryRows = MutableStateFlow(listOf(manga(1, "Manga 1"))),
            chaptersMap = mapOf(
                1L to listOf(
                    chapter(101, 1L, read = false),
                    chapter(102, 1L, read = false),
                ),
            ),
        )
        val presenter = LibraryPresenter(repo, scope)
        presenter.awaitState { !it.loading }

        presenter.toggleMangaSelection(1L)
        presenter.awaitState { it.selectionState.selectedMangaIds.size == 1 }

        // Mark read
        presenter.batchMarkRead(true)
        presenter.awaitState { it.selectionState.selectedMangaIds.isEmpty() }

        repo.chapterRecords[101L]?.read shouldBe true
        repo.chapterRecords[102L]?.read shouldBe true

        // Mark unread
        presenter.toggleMangaSelection(1L)
        presenter.awaitState { it.selectionState.selectedMangaIds.size == 1 }
        presenter.batchMarkRead(false)
        presenter.awaitState { it.selectionState.selectedMangaIds.isEmpty() }

        repo.chapterRecords[101L]?.read shouldBe false
        repo.chapterRecords[102L]?.read shouldBe false

        presenter.close()
    }

    @Test
    fun `batchRemoveFromLibrary marks selected manga as favorite false`() = runBlocking {
        val repo = BatchTestFakeRepository(
            libraryRows = MutableStateFlow(listOf(manga(1, "Manga 1"))),
            mangaRecordsMap = mutableMapOf(
                "/1" to MangaRecord(
                    id = 1L,
                    sourceId = 101L,
                    url = "/1",
                    title = "Manga 1",
                    favorite = true,
                ),
            ),
        )
        val presenter = LibraryPresenter(repo, scope)
        presenter.awaitState { !it.loading }

        presenter.toggleMangaSelection(1L)
        presenter.awaitState { it.selectionState.selectedMangaIds.size == 1 }

        presenter.batchRemoveFromLibrary()
        presenter.awaitState { it.selectionState.selectedMangaIds.isEmpty() }

        repo.mangaRecordsMap["/1"]?.favorite shouldBe false

        presenter.close()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `batch action bar triggers callbacks for actions`() = runComposeUiTest {
        var selectAllCalled = false
        var changeCategoryCalled = false
        var markReadValue: Boolean? = null
        var exitCalled = false

        setContent {
            MaterialTheme {
                Box(modifier = Modifier.requiredSize(1000.dp, 100.dp)) {
                    LibraryBatchActionBar(
                        selectedCount = 2,
                        totalCount = 5,
                        onSelectAll = { selectAllCalled = true },
                        onDeselectAll = {},
                        onChangeCategories = { changeCategoryCalled = true },
                        onMarkRead = { markReadValue = it },
                        onDownloadChapters = {},
                        onRemoveFromLibrary = {},
                        onExitSelection = { exitCalled = true },
                    )
                }
            }
        }

        onNodeWithTag("batch-selected-count").assertExists()
        onNodeWithTag("batch-select-all").performClick()
        selectAllCalled shouldBe true

        onNodeWithTag("batch-change-category").performClick()
        changeCategoryCalled shouldBe true

        onNodeWithTag("batch-mark-read").performClick()
        markReadValue shouldBe true

        onNodeWithTag("batch-mark-unread").performClick()
        markReadValue shouldBe false

        onNodeWithTag("batch-exit-button").performClick()
        exitCalled shouldBe true
    }

    private suspend fun LibraryPresenter.awaitState(predicate: (LibraryUiState) -> Boolean): LibraryUiState =
        withTimeout(5_000) { state.first(predicate) }

    private fun manga(id: Long, title: String) = LibraryManga(
        id = id,
        sourceId = 100 + id,
        url = "/$id",
        title = title,
        thumbnailUrl = null,
        chapterCount = 10L,
        unreadCount = 2L,
        author = null,
    )

    private fun chapter(id: Long, mangaId: Long, read: Boolean) = LibraryChapter(
        id = id,
        mangaId = mangaId,
        url = "/ch/$id",
        name = "Chapter $id",
        scanlator = null,
        read = read,
        bookmark = false,
        lastPageRead = 0L,
        dateFetch = 0L,
        dateUpload = 0L,
        chapterNumber = id.toDouble(),
        sourceOrder = id,
        lastModifiedAt = 0L,
        version = 0L,
        memoJson = "{}",
    )
}

private class BatchTestFakeRepository(
    private val libraryRows: MutableStateFlow<List<LibraryManga>>,
    private val chaptersMap: Map<Long, List<LibraryChapter>> = emptyMap(),
    val mangaRecordsMap: MutableMap<String, MangaRecord> = mutableMapOf(),
) : LibraryRepository, LibraryMutationPort {
    val mangaCategoryMap = mutableMapOf<Long, List<Long>>()
    val chapterRecords = mutableMapOf<Long, ChapterRecord>()

    init {
        for ((mangaId, chList) in chaptersMap) {
            for (ch in chList) {
                chapterRecords[ch.id] = ChapterRecord(
                    id = ch.id,
                    mangaId = mangaId,
                    url = ch.url,
                    name = ch.name,
                    scanlator = ch.scanlator,
                    read = ch.read,
                    bookmark = ch.bookmark,
                    lastPageRead = ch.lastPageRead,
                    chapterNumber = ch.chapterNumber,
                    sourceOrder = ch.sourceOrder,
                    dateFetch = ch.dateFetch,
                    dateUpload = ch.dateUpload,
                    lastModifiedAt = ch.lastModifiedAt,
                    version = ch.version,
                    memoJson = ch.memoJson,
                )
            }
        }
    }

    override fun observeLibrary(categoryId: Long?): Flow<List<LibraryManga>> = libraryRows
    override fun observeManga(id: Long): Flow<MangaDetails?> = flowOf(null)
    override fun observeChapters(mangaId: Long): Flow<List<LibraryChapter>> = flowOf(emptyList())
    override fun observeCategories(): Flow<List<CategoryRecord>> = flowOf(emptyList())
    override fun observeHistory(query: String): Flow<List<HistoryWithDetails>> = flowOf(emptyList())
    override fun observeTracking(mangaId: Long): Flow<List<TrackingRecord>> = flowOf(emptyList())
    override fun librarySnapshot(categoryId: Long?): List<LibraryManga> = libraryRows.value
    override fun mangaSnapshot(id: Long): MangaDetails? = null
    override fun chapterSnapshot(mangaId: Long): List<LibraryChapter> = chaptersMap[mangaId] ?: emptyList()
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

    // Mutation Port
    override fun <T> transaction(block: LibraryMutationPort.() -> T): T = block()
    override fun findManga(sourceId: Long, url: String): MangaRecord? = mangaRecordsMap[url]
    override fun insertManga(value: MangaRecord): Long = 0L
    override fun updateManga(value: MangaRecord) {
        mangaRecordsMap[value.url] = value
    }
    override fun findChapter(mangaId: Long, url: String): ChapterRecord? =
        chapterRecords.values.find { it.mangaId == mangaId && it.url == url }
    override fun insertChapter(value: ChapterRecord): Long = 0L
    override fun updateChapter(value: ChapterRecord) {
        chapterRecords[value.id] = value
    }
    override fun upsertCategory(value: CategoryRecord): Long = 0L
    override fun deleteCategory(categoryId: Long) {}
    override fun updateCategoryName(categoryId: Long, name: String) {}
    override fun updateCategoryOrder(categoryId: Long, sortOrder: Long) {}
    override fun linkCategory(mangaId: Long, categoryId: Long) {}
    override fun unlinkCategory(mangaId: Long, categoryId: Long) {}
    override fun setMangaCategories(mangaId: Long, categoryIds: List<Long>) {
        mangaCategoryMap[mangaId] = categoryIds
    }
    override fun upsertHistory(value: HistoryRecord) {}
    override fun deleteHistory(chapterId: Long) {}
    override fun clearAllHistory() {}
    override fun findTracking(mangaId: Long, trackerId: Long): TrackingRecord? = null
    override fun insertTracking(value: TrackingRecord) {}
    override fun updateTracking(value: TrackingRecord) {}
    override fun deleteTracking(mangaId: Long, trackerId: Long) {}
    override fun upsertSource(value: SourceRecord) {}
    override fun upsertPreference(value: PreferenceSnapshotRecord) {}
    override fun upsertSourcePreference(value: SourcePreferenceSnapshotRecord) {}
    override fun findLocalMangaByManifest(manifestSha256: String): LocalMangaRecord? = null
    override fun localMangaStoragePaths(): Set<String> = emptySet()
    override fun insertLocalManga(value: LocalMangaRecord) {}
    override fun insertLocalChapter(value: LocalChapterRecord) {}
    override fun insertReport(value: ImportReportRecord): Long = 0L
    override fun insertReportItem(reportId: Long, value: ImportReportItemRecord) {}
}
