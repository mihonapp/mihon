package mihon.desktop.ui.library

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
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

class EditMangaInfoTest {
    private val parentJob = SupervisorJob()
    private val scope = CoroutineScope(parentJob + Dispatchers.Default)

    @AfterEach
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `updateMangaInfo modifies fields and saves to mutation port`() = runBlocking {
        val original = MangaRecord(
            id = 42L,
            sourceId = 1L,
            url = "/manga/42",
            title = "Original Title",
            author = "Orig Author",
            artist = "Orig Artist",
            description = "Orig Desc",
            genreJson = "[\"Action\"]",
            status = 1L,
            notes = "",
        )

        var savedManga: MangaRecord? = null

        val fakeMutationPort = object : LibraryMutationPort {
            override fun <T> transaction(block: LibraryMutationPort.() -> T): T = block()
            override fun findManga(sourceId: Long, url: String): MangaRecord? = original
            override fun insertManga(value: MangaRecord): Long = value.id
            override fun updateManga(value: MangaRecord) {
                savedManga = value
            }
            override fun findChapter(mangaId: Long, url: String): ChapterRecord? = null
            override fun insertChapter(value: ChapterRecord): Long = 0L
            override fun updateChapter(value: ChapterRecord) {}
            override fun upsertCategory(value: CategoryRecord): Long = 0L
            override fun deleteCategory(categoryId: Long) {}
            override fun updateCategoryName(categoryId: Long, name: String) {}
            override fun updateCategoryOrder(categoryId: Long, sortOrder: Long) {}
            override fun linkCategory(mangaId: Long, categoryId: Long) {}
            override fun unlinkCategory(mangaId: Long, categoryId: Long) {}
            override fun setMangaCategories(mangaId: Long, categoryIds: List<Long>) {}
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

        val fakeRepo = object : LibraryRepository {
            override fun observeLibrary(categoryId: Long?): Flow<List<LibraryManga>> = emptyFlow()
            override fun observeManga(id: Long): Flow<MangaDetails?> = emptyFlow()
            override fun observeChapters(mangaId: Long): Flow<List<LibraryChapter>> = emptyFlow()
            override fun observeCategories(): Flow<List<CategoryRecord>> = emptyFlow()
            override fun observeHistory(query: String): Flow<List<HistoryWithDetails>> = emptyFlow()
            override fun observeTracking(mangaId: Long): Flow<List<TrackingRecord>> = emptyFlow()
            override fun librarySnapshot(categoryId: Long?): List<LibraryManga> = emptyList()
            override fun mangaSnapshot(id: Long): MangaDetails? = null
            override fun chapterSnapshot(mangaId: Long): List<LibraryChapter> = emptyList()
            override fun categoriesSnapshot(): List<CategoryRecord> = emptyList()
            override fun historySnapshot(query: String): List<HistoryWithDetails> = emptyList()
            override fun trackingSnapshot(mangaId: Long): List<TrackingRecord> = emptyList()
            override fun latestImportReport(): ImportReport? = null
            override fun allMangaSnapshot(): List<MangaRecord> = listOf(original)
            override fun allChaptersSnapshot(): List<ChapterRecord> = emptyList()
            override fun allCategoriesSnapshot(): List<CategoryRecord> = emptyList()
            override fun mangaCategoryLinksSnapshot(): Map<Long, List<Long>> = emptyMap()
            override fun allHistorySnapshot(): List<HistoryRecord> = emptyList()
            override fun allTrackingSnapshot(): List<TrackingRecord> = emptyList()
            override fun allSourcesSnapshot(): List<SourceRecord> = emptyList()
            override fun allPreferenceSnapshots(): List<PreferenceSnapshotRecord> = emptyList()
            override fun allSourcePreferenceSnapshots(): List<SourcePreferenceSnapshotRecord> = emptyList()
            override fun checkIntegrity(): List<String> = emptyList()
        }

        val presenter = LibraryPresenter(
            repository = fakeRepo,
            scope = scope,
            mutationPort = fakeMutationPort,
        )

        presenter.updateMangaInfo(
            mangaId = 42L,
            title = "Custom New Title",
            author = "New Author",
            artist = "New Artist",
            description = "New Description",
            genres = listOf("Romance", "Comedy"),
            status = 2L,
            notes = "Must read this weekend!",
        )

        savedManga?.title shouldBe "Custom New Title"
        savedManga?.author shouldBe "New Author"
        savedManga?.artist shouldBe "New Artist"
        savedManga?.description shouldBe "New Description"
        savedManga?.status shouldBe 2L
        savedManga?.notes shouldBe "Must read this weekend!"

        val genres = Json.parseToJsonElement(savedManga?.genreJson ?: "[]").jsonArray.map { it.jsonPrimitive.content }
        genres shouldBe listOf("Romance", "Comedy")
    }
}
