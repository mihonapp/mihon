package mihon.desktop.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mihon.desktop.category.DesktopCategory
import mihon.desktop.category.DesktopCategoryService
import mihon.desktop.category.SYSTEM_ALL_CATEGORY
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
import mihon.desktop.ui.category.EditMangaCategoriesDialog
import mihon.desktop.ui.category.ManageCategoriesDialog
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList

class CategoryManagementTest {
    private val parentJob = SupervisorJob()
    private val scope = CoroutineScope(parentJob + Dispatchers.Default)

    @AfterEach
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `category service supports create rename delete and reorder through repository ports`() = runBlocking {
        val fake = FakeCategoryLibrary()
        val service = DesktopCategoryService(repository = fake, mutationPort = fake, scope = scope)

        service.createCategory("Action")
        val created = service.awaitCategories { it.size == 1 }.single()
        created.name shouldBe "Action"
        created.order shouldBe 1L

        service.createCategory("Drama")
        val afterSecondCreate = service.awaitCategories { it.size == 2 }
        val drama = afterSecondCreate.single { it.name == "Drama" }
        drama.order shouldBe 2L

        service.renameCategory(created.id, "Adventure")
        service.awaitCategories { categories -> categories.any { it.id == created.id && it.name == "Adventure" } }

        service.reorderCategory(created.id, 0L)
        service.awaitCategories { categories ->
            categories.single { it.id == created.id }.order == 0L
        }

        service.deleteCategory(drama.id)
        service.awaitCategories { categories -> categories.none { it.id == drama.id } }.size shouldBe 1
        Unit
    }

    @Test
    fun `category selection switches repository query and keeps the system all category id`() = runBlocking {
        val fake = FakeCategoryLibrary()
        fake.libraryItemsForCategory = { categoryId ->
            when (categoryId) {
                null, SYSTEM_ALL_CATEGORY.id -> fake.allManga
                10L -> listOf(fake.allManga.first())
                else -> emptyList()
            }
        }
        val presenter = LibraryPresenter(fake, scope)

        val all = presenter.awaitState {
            !it.loading && it.selectedCategoryId == SYSTEM_ALL_CATEGORY.id && it.items.size == 2
        }
        all.selectedCategoryId shouldBe SYSTEM_ALL_CATEGORY.id
        fake.observedCategoryIds.last() shouldBe SYSTEM_ALL_CATEGORY.id

        presenter.selectCategory(10L)
        val filtered = presenter.awaitState { it.selectedCategoryId == 10L && it.items.size == 1 }
        filtered.items.single().id shouldBe 1L
        fake.observedCategoryIds.last() shouldBe 10L

        presenter.selectCategory(SYSTEM_ALL_CATEGORY.id)
        presenter.awaitState { it.selectedCategoryId == SYSTEM_ALL_CATEGORY.id && it.items.size == 2 }
        fake.observedCategoryIds.last() shouldBe SYSTEM_ALL_CATEGORY.id

        presenter.close()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `manage categories dialog exposes create rename delete and reorder actions`() = runComposeUiTest {
        var createdName: String? = null
        var renamed: Pair<Long, String>? = null
        var deletedId: Long? = null
        val moves = mutableListOf<Pair<Long, Int>>()

        setContent {
            MaterialTheme {
                Box(modifier = Modifier.requiredSize(1000.dp, 800.dp)) {
                    ManageCategoriesDialog(
                        categories = listOf(
                            DesktopCategory(id = 1L, name = "Action", order = 0L),
                            DesktopCategory(id = 2L, name = "Drama", order = 1L),
                        ),
                        onDismiss = {},
                        onCreateCategory = { createdName = it },
                        onRenameCategory = { id, name -> renamed = id to name },
                        onDeleteCategory = { deletedId = it },
                        onMoveCategory = { category, newIndex -> moves += category.id to newIndex },
                    )
                }
            }
        }

        onNodeWithTag("manage-categories-dialog").assertExists()
        onNodeWithTag("move-category-up-1").assertIsNotEnabled()
        onNodeWithTag("move-category-down-2").assertIsNotEnabled()

        onNodeWithTag("create-category-input").performTextInput("Comedy")
        onNodeWithTag("create-category-button").performClick()
        createdName shouldBe "Comedy"

        onNodeWithTag("rename-category-1").performClick()
        onNodeWithTag("rename-category-input").performTextReplacement("Adventure")
        onNodeWithTag("confirm-rename-category-button").performClick()
        renamed shouldBe (1L to "Adventure")

        onNodeWithTag("delete-category-2").performClick()
        deletedId shouldBe 2L

        onNodeWithTag("move-category-down-1").performClick()
        onNodeWithTag("move-category-up-2").performClick()
        moves shouldBe listOf(1L to 1, 2L to 0)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `edit manga categories dialog preloads current ids and saves the selection`() = runComposeUiTest {
        var savedIds: List<Long>? = null

        setContent {
            MaterialTheme {
                Box(modifier = Modifier.requiredSize(600.dp, 600.dp)) {
                    EditMangaCategoriesDialog(
                        allCategories = listOf(
                            DesktopCategory(id = 1L, name = "Action", order = 0L),
                            DesktopCategory(id = 2L, name = "Drama", order = 1L),
                        ),
                        assignedCategoryIds = setOf(1L),
                        onDismiss = {},
                        onSave = { savedIds = it },
                    )
                }
            }
        }

        onNodeWithTag("edit-manga-categories-dialog").assertExists()
        onNodeWithTag("manga-category-checkbox-1").assertIsOn()
        onNodeWithTag("manga-category-checkbox-2").assertIsOff()

        onNodeWithTag("manga-category-checkbox-2").performClick()
        onNodeWithTag("save-manga-categories-button").performClick()

        savedIds?.sorted() shouldBe listOf(1L, 2L)
    }

    private suspend fun LibraryPresenter.awaitState(predicate: (LibraryUiState) -> Boolean): LibraryUiState =
        withTimeout(5_000) { state.first(predicate) }

    private suspend fun DesktopCategoryService.awaitCategories(
        predicate: (List<DesktopCategory>) -> Boolean,
    ): List<DesktopCategory> = withTimeout(5_000) { categories.first(predicate) }
}

private class FakeCategoryLibrary : LibraryRepository, LibraryMutationPort {
    val categoryRecords = MutableStateFlow<List<CategoryRecord>>(emptyList())
    val observedCategoryIds = CopyOnWriteArrayList<Long?>()
    val allManga = listOf(
        LibraryManga(
            id = 1L,
            sourceId = 101L,
            url = "/1",
            title = "One",
            thumbnailUrl = null,
            chapterCount = 10L,
            unreadCount = 2L,
        ),
        LibraryManga(
            id = 2L,
            sourceId = 102L,
            url = "/2",
            title = "Two",
            thumbnailUrl = null,
            chapterCount = 20L,
            unreadCount = 0L,
        ),
    )
    var libraryItemsForCategory: (Long?) -> List<LibraryManga> = { emptyList() }
    var savedMangaCategories: Pair<Long, List<Long>>? = null
    private var nextCategoryId = 1L

    override fun observeLibrary(categoryId: Long?): Flow<List<LibraryManga>> {
        observedCategoryIds += categoryId
        return flowOf(libraryItemsForCategory(categoryId))
    }

    override fun observeManga(id: Long): Flow<MangaDetails?> = flowOf<MangaDetails?>(null)
    override fun observeChapters(mangaId: Long): Flow<List<LibraryChapter>> = flowOf(emptyList())
    override fun observeCategories(): Flow<List<CategoryRecord>> = categoryRecords
    override fun observeHistory(query: String): Flow<List<HistoryWithDetails>> = flowOf(emptyList())
    override fun observeTracking(mangaId: Long): Flow<List<TrackingRecord>> = flowOf(emptyList())
    override fun librarySnapshot(categoryId: Long?): List<LibraryManga> = libraryItemsForCategory(categoryId)
    override fun mangaSnapshot(id: Long): MangaDetails? = null
    override fun chapterSnapshot(mangaId: Long): List<LibraryChapter> = emptyList()
    override fun categoriesSnapshot(): List<CategoryRecord> = categoryRecords.value
    override fun historySnapshot(query: String): List<HistoryWithDetails> = emptyList()
    override fun trackingSnapshot(mangaId: Long): List<TrackingRecord> = emptyList()
    override fun latestImportReport(): ImportReport? = null
    override fun allMangaSnapshot(): List<MangaRecord> = emptyList()
    override fun allChaptersSnapshot(): List<ChapterRecord> = emptyList()
    override fun allCategoriesSnapshot(): List<CategoryRecord> = categoryRecords.value
    override fun mangaCategoryLinksSnapshot(): Map<Long, List<Long>> = emptyMap()
    override fun allHistorySnapshot(): List<HistoryRecord> = emptyList()
    override fun allTrackingSnapshot(): List<TrackingRecord> = emptyList()
    override fun allSourcesSnapshot(): List<SourceRecord> = emptyList()
    override fun allPreferenceSnapshots(): List<PreferenceSnapshotRecord> = emptyList()
    override fun allSourcePreferenceSnapshots(): List<SourcePreferenceSnapshotRecord> = emptyList()
    override fun checkIntegrity(): List<String> = emptyList()

    override fun <T> transaction(block: LibraryMutationPort.() -> T): T = block()
    override fun findManga(sourceId: Long, url: String): MangaRecord? = null
    override fun insertManga(value: MangaRecord): Long = 0L
    override fun updateManga(value: MangaRecord) = Unit
    override fun findChapter(mangaId: Long, url: String): ChapterRecord? = null
    override fun insertChapter(value: ChapterRecord): Long = 0L
    override fun updateChapter(value: ChapterRecord) = Unit

    override fun upsertCategory(value: CategoryRecord): Long {
        val id = if (value.id != 0L) value.id else nextCategoryId++
        categoryRecords.update { records ->
            records.filterNot { it.id == id } + value.copy(id = id)
        }
        return id
    }

    override fun deleteCategory(categoryId: Long) {
        categoryRecords.update { records -> records.filterNot { it.id == categoryId } }
    }

    override fun updateCategoryName(categoryId: Long, name: String) {
        categoryRecords.update { records ->
            records.map { if (it.id == categoryId) it.copy(name = name) else it }
        }
    }

    override fun updateCategoryOrder(categoryId: Long, sortOrder: Long) {
        categoryRecords.update { records ->
            records.map { if (it.id == categoryId) it.copy(sortOrder = sortOrder) else it }
        }
    }

    override fun linkCategory(mangaId: Long, categoryId: Long) = Unit
    override fun unlinkCategory(mangaId: Long, categoryId: Long) = Unit

    override fun setMangaCategories(mangaId: Long, categoryIds: List<Long>) {
        savedMangaCategories = mangaId to categoryIds
    }

    override fun upsertHistory(value: HistoryRecord) = Unit
    override fun deleteHistory(chapterId: Long) = Unit
    override fun clearAllHistory() = Unit
    override fun findTracking(mangaId: Long, trackerId: Long): TrackingRecord? = null
    override fun insertTracking(value: TrackingRecord) = Unit
    override fun updateTracking(value: TrackingRecord) = Unit
    override fun deleteTracking(mangaId: Long, trackerId: Long) = Unit
    override fun upsertSource(value: SourceRecord) = Unit
    override fun upsertPreference(value: PreferenceSnapshotRecord) = Unit
    override fun upsertSourcePreference(value: SourcePreferenceSnapshotRecord) = Unit
    override fun findLocalMangaByManifest(manifestSha256: String): LocalMangaRecord? = null
    override fun localMangaStoragePaths(): Set<String> = emptySet()
    override fun insertLocalManga(value: LocalMangaRecord) = Unit
    override fun insertLocalChapter(value: LocalChapterRecord) = Unit
    override fun insertReport(value: ImportReportRecord): Long = 0L
    override fun insertReportItem(reportId: Long, value: ImportReportItemRecord) = Unit
}
