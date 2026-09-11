package mihon.desktop.ui.library

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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

internal class TestLibraryRepository : LibraryRepository, LibraryMutationPort {
    private val manga = LinkedHashMap<Long, MangaRecord>()
    private val chapters = LinkedHashMap<Long, ChapterRecord>()
    private val links = mutableMapOf<Long, MutableList<Long>>()
    private val tracking = mutableListOf<TrackingRecord>()
    private val categories = mutableListOf<CategoryRecord>()
    private val mangaFlows = mutableMapOf<Long, MutableStateFlow<MangaDetails?>>()
    private val chapterFlows = mutableMapOf<Long, MutableStateFlow<List<LibraryChapter>>>()
    private var nextId = 1000L

    val libraryFlow = MutableStateFlow<List<LibraryManga>>(emptyList())

    fun addManga(record: MangaRecord, chapters: List<ChapterRecord> = emptyList()): Long {
        val mangaId = if (record.id == 0L) nextId++ else record.id
        val stored = record.copy(id = mangaId)
        manga[mangaId] = stored
        chapters.forEach { chapter ->
            val chapterId = if (chapter.id == 0L) nextId++ else chapter.id
            this.chapters[chapterId] = chapter.copy(id = chapterId, mangaId = mangaId)
        }
        refreshLibrary()
        mangaFlows[mangaId]?.value = stored.toDetails()
        chapterFlows[mangaId]?.value = chaptersFor(mangaId)
        return mangaId
    }

    fun addCategoryLink(mangaId: Long, categoryId: Long) {
        links.getOrPut(mangaId) { mutableListOf() }.add(categoryId)
    }

    fun addTracking(record: TrackingRecord) {
        tracking += record
    }

    override fun observeLibrary(categoryId: Long?): Flow<List<LibraryManga>> = libraryFlow

    override fun observeManga(id: Long): Flow<MangaDetails?> = mangaFlows.getOrPut(id) {
        MutableStateFlow(manga[id]?.toDetails())
    }

    override fun observeChapters(mangaId: Long): Flow<List<LibraryChapter>> = chapterFlows.getOrPut(mangaId) {
        MutableStateFlow(chaptersFor(mangaId))
    }

    override fun observeCategories(): Flow<List<CategoryRecord>> = flowOf(categories)

    override fun observeHistory(query: String): Flow<List<HistoryWithDetails>> = flowOf(emptyList())

    override fun observeTracking(mangaId: Long): Flow<List<TrackingRecord>> = flowOf(trackingSnapshot(mangaId))

    override fun librarySnapshot(categoryId: Long?): List<LibraryManga> = libraryFlow.value

    override fun mangaSnapshot(id: Long): MangaDetails? = manga[id]?.toDetails()

    override fun chapterSnapshot(mangaId: Long): List<LibraryChapter> = chaptersFor(mangaId)

    override fun categoriesSnapshot(): List<CategoryRecord> = categories.toList()

    override fun historySnapshot(query: String): List<HistoryWithDetails> = emptyList()

    override fun trackingSnapshot(mangaId: Long): List<TrackingRecord> = tracking.filter { it.mangaId == mangaId }

    override fun latestImportReport(): ImportReport? = null

    override fun allMangaSnapshot(): List<MangaRecord> = manga.values.toList()

    override fun allChaptersSnapshot(): List<ChapterRecord> = chapters.values.toList()

    override fun allCategoriesSnapshot(): List<CategoryRecord> = categories.toList()

    override fun mangaCategoryLinksSnapshot(): Map<Long, List<Long>> = links.mapValues { it.value.toList() }

    override fun allHistorySnapshot(): List<HistoryRecord> = emptyList()

    override fun allTrackingSnapshot(): List<TrackingRecord> = tracking.toList()

    override fun allSourcesSnapshot(): List<SourceRecord> = emptyList()

    override fun allPreferenceSnapshots(): List<PreferenceSnapshotRecord> = emptyList()

    override fun allSourcePreferenceSnapshots(): List<SourcePreferenceSnapshotRecord> = emptyList()

    override fun checkIntegrity(): List<String> = emptyList()

    override fun <T> transaction(block: LibraryMutationPort.() -> T): T = block()

    override fun findManga(sourceId: Long, url: String): MangaRecord? =
        manga.values.firstOrNull { it.sourceId == sourceId && it.url == url }

    override fun insertManga(value: MangaRecord): Long = addManga(value)

    override fun updateManga(value: MangaRecord) {
        manga[value.id] = value
        mangaFlows[value.id]?.value = value.toDetails()
        refreshLibrary()
    }

    override fun findChapter(mangaId: Long, url: String): ChapterRecord? =
        chapters.values.firstOrNull { it.mangaId == mangaId && it.url == url }

    override fun insertChapter(value: ChapterRecord): Long {
        val chapterId = if (value.id == 0L) nextId++ else value.id
        chapters[chapterId] = value.copy(id = chapterId)
        chapterFlows[value.mangaId]?.value = chaptersFor(value.mangaId)
        refreshLibrary()
        return chapterId
    }

    override fun updateChapter(value: ChapterRecord) {
        val previous = chapters[value.id]
        chapters[value.id] = value
        previous?.mangaId?.let { chapterFlows[it]?.value = chaptersFor(it) }
        chapterFlows[value.mangaId]?.value = chaptersFor(value.mangaId)
        refreshLibrary()
    }

    override fun upsertCategory(value: CategoryRecord): Long {
        categories.removeAll { it.id == value.id }
        categories += value
        return value.id
    }

    override fun deleteCategory(categoryId: Long) {
        categories.removeAll { it.id == categoryId }
    }

    override fun updateCategoryName(categoryId: Long, name: String) = Unit

    override fun updateCategoryOrder(categoryId: Long, sortOrder: Long) = Unit

    override fun linkCategory(mangaId: Long, categoryId: Long) {
        links.getOrPut(mangaId) { mutableListOf() }.let { if (categoryId !in it) it += categoryId }
    }

    override fun unlinkCategory(mangaId: Long, categoryId: Long) {
        links[mangaId]?.remove(categoryId)
    }

    override fun setMangaCategories(mangaId: Long, categoryIds: List<Long>) {
        links[mangaId] = categoryIds.toMutableList()
    }

    override fun upsertHistory(value: HistoryRecord) = Unit

    override fun deleteHistory(chapterId: Long) = Unit

    override fun clearAllHistory() = Unit

    override fun findTracking(mangaId: Long, trackerId: Long): TrackingRecord? =
        tracking.firstOrNull { it.mangaId == mangaId && it.trackerId == trackerId }

    override fun insertTracking(value: TrackingRecord) {
        tracking += value
    }

    override fun updateTracking(value: TrackingRecord) {
        tracking.removeAll { it.id == value.id }
        tracking += value
    }

    override fun deleteTracking(mangaId: Long, trackerId: Long) {
        tracking.removeAll { it.mangaId == mangaId && it.trackerId == trackerId }
    }

    override fun upsertSource(value: SourceRecord) = Unit

    override fun upsertPreference(value: PreferenceSnapshotRecord) = Unit

    override fun upsertSourcePreference(value: SourcePreferenceSnapshotRecord) = Unit

    override fun findLocalMangaByManifest(manifestSha256: String): LocalMangaRecord? = null

    override fun localMangaStoragePaths(): Set<String> = emptySet()

    override fun insertLocalManga(value: LocalMangaRecord) = Unit

    override fun insertLocalChapter(value: LocalChapterRecord) = Unit

    override fun insertReport(value: ImportReportRecord): Long = 0L

    override fun insertReportItem(reportId: Long, value: ImportReportItemRecord) = Unit

    private fun chaptersFor(mangaId: Long): List<LibraryChapter> =
        chapters.values.filter { it.mangaId == mangaId }.map { it.toLibraryChapter() }

    private fun refreshLibrary() {
        libraryFlow.value = manga.values
            .filter { it.favorite }
            .map { record ->
                val recordChapters = chapters.values.filter { it.mangaId == record.id }
                LibraryManga(
                    id = record.id,
                    sourceId = record.sourceId,
                    url = record.url,
                    title = record.title,
                    thumbnailUrl = record.thumbnailUrl,
                    chapterCount = recordChapters.size.toLong(),
                    unreadCount = recordChapters.count { !it.read }.toLong(),
                    author = record.author,
                )
            }
    }

    private fun MangaRecord.toDetails(): MangaDetails = MangaDetails(
        id = id,
        sourceId = sourceId,
        url = url,
        title = title,
        artist = artist,
        author = author,
        description = description,
        genreJson = genreJson,
        status = status,
        thumbnailUrl = thumbnailUrl,
        favorite = favorite,
        dateAdded = dateAdded,
        viewerFlags = viewerFlags,
        chapterFlags = chapterFlags,
        updateStrategy = updateStrategy,
        lastModifiedAt = lastModifiedAt,
        favoriteModifiedAt = favoriteModifiedAt,
        excludedScanlatorsJson = excludedScanlatorsJson,
        version = version,
        notes = notes,
        initialized = initialized,
        memoJson = memoJson,
        categories = links[id].orEmpty().map { CategoryRecord(id = it, name = "Category $it") },
    )

    private fun ChapterRecord.toLibraryChapter(): LibraryChapter = LibraryChapter(
        id = id,
        mangaId = mangaId,
        url = url,
        name = name,
        scanlator = scanlator,
        read = read,
        bookmark = bookmark,
        lastPageRead = lastPageRead,
        dateFetch = dateFetch,
        dateUpload = dateUpload,
        chapterNumber = chapterNumber,
        sourceOrder = sourceOrder,
        lastModifiedAt = lastModifiedAt,
        version = version,
        memoJson = memoJson,
    )
}
