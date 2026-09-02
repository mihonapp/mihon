package mihon.desktop.library.db

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mihon.desktop.library.model.CategoryRecord
import mihon.desktop.library.model.ChapterRecord
import mihon.desktop.library.model.HistoryRecord
import mihon.desktop.library.model.ImportCounts
import mihon.desktop.library.model.ImportReport
import mihon.desktop.library.model.ImportReportItem
import mihon.desktop.library.model.ImportReportItemRecord
import mihon.desktop.library.model.ImportReportRecord
import mihon.desktop.library.model.ImportStatus
import mihon.desktop.library.model.ImportType
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
import mihon.desktop.library.reader.ReaderLibraryPort
import mihon.desktop.library.repository.LibraryMutationPort
import mihon.desktop.library.repository.LibraryRepository
import mihon.reader.session.ProgressWriteResult
import mihon.reader.session.ReaderProgressUpdate
import mihon.reader.source.ChapterDirection
import mihon.reader.source.ReaderChapterAsset
import java.nio.file.Path

class SqlDelightLibraryRepository(
    private val driver: SqlDriver,
    private val database: DesktopLibraryDatabase,
) : LibraryRepository, LibraryMutationPort, ReaderLibraryPort, AutoCloseable {
    private val queries = database.libraryQueries
    private val readerProgressMutex = Mutex()
    private val acceptedReaderProgress = mutableMapOf<Long, ReaderProgressVersion>()

    override fun observeLibrary(): Flow<List<LibraryManga>> =
        queries.selectLibrary().asFlow().mapToList(Dispatchers.IO).map { rows -> rows.map(SelectLibrary::toModel) }

    override fun observeManga(id: Long): Flow<MangaDetails?> = combine(
        queries.selectMangaById(id).asFlow().mapToOneOrNull(Dispatchers.IO),
        queries.selectCategoriesForManga(id).asFlow().mapToList(Dispatchers.IO),
    ) { manga, categories -> manga?.toDetails(categories.map(Category::toRecord)) }

    override fun observeChapters(mangaId: Long): Flow<List<LibraryChapter>> =
        queries.selectChaptersForManga(mangaId).asFlow().mapToList(Dispatchers.IO)
            .map { rows -> rows.map(Chapter::toModel) }

    override fun librarySnapshot(): List<LibraryManga> =
        queries.selectLibrary().executeAsList().map(SelectLibrary::toModel)

    override fun mangaSnapshot(id: Long): MangaDetails? {
        val manga = queries.selectMangaById(id).executeAsOneOrNull() ?: return null
        return manga.toDetails(queries.selectCategoriesForManga(id).executeAsList().map(Category::toRecord))
    }

    override fun chapterSnapshot(mangaId: Long): List<LibraryChapter> =
        queries.selectChaptersForManga(mangaId).executeAsList().map(Chapter::toModel)

    override fun chapterAsset(chapterId: Long): ReaderChapterAsset? =
        queries.selectReaderChapterAsset(chapterId).executeAsOneOrNull()?.toReaderChapterAsset()

    override fun adjacentReadableChapter(
        chapterId: Long,
        direction: ChapterDirection,
    ): ReaderChapterAsset? = database.transactionWithResult {
        if (chapterAsset(chapterId) == null) return@transactionWithResult null
        when (direction) {
            ChapterDirection.PREVIOUS ->
                queries.selectPreviousReaderChapterAsset(chapterId).executeAsList()
                    .firstNotNullOfOrNull(SelectPreviousReaderChapterAsset::toReaderChapterAsset)
            ChapterDirection.NEXT ->
                queries.selectNextReaderChapterAsset(chapterId).executeAsList()
                    .firstNotNullOfOrNull(SelectNextReaderChapterAsset::toReaderChapterAsset)
        }
    }

    override suspend fun record(update: ReaderProgressUpdate): ProgressWriteResult {
        return readerProgressMutex.withLock {
            validateReaderProgress(update)
            val incoming = ReaderProgressVersion(update.generation, update.sequence)
            val accepted = acceptedReaderProgress[update.chapterId]
            if (accepted != null && incoming <= accepted) return@withLock ProgressWriteResult.STALE

            database.transaction {
                queries.updateReaderChapterProgress(
                    last_page_read = update.pageIndex,
                    completed = update.completed,
                    chapter_id = update.chapterId,
                )
                queries.accumulateReaderHistory(
                    chapter_id = update.chapterId,
                    last_read = update.lastReadEpochMillis,
                    read_duration_delta = update.readDurationDeltaMillis,
                )
            }
            acceptedReaderProgress[update.chapterId] = incoming
            ProgressWriteResult.APPLIED
        }
    }

    override fun latestImportReport(): ImportReport? {
        val report = queries.selectLatestImportReport().executeAsOneOrNull() ?: return null
        val items = queries.selectImportReportItems(report.id).executeAsList().map(Import_report_item::toModel)
        return report.toModel(items)
    }

    override fun <T> transaction(block: LibraryMutationPort.() -> T): T =
        database.transactionWithResult { block(this@SqlDelightLibraryRepository) }

    override fun findManga(sourceId: Long, url: String): MangaRecord? =
        queries.selectMangaByIdentity(sourceId, url).executeAsOneOrNull()?.toRecord()

    override fun insertManga(value: MangaRecord): Long = database.transactionWithResult {
        queries.insertManga(
            source_id = value.sourceId,
            url = value.url,
            title = value.title,
            artist = value.artist,
            author = value.author,
            description = value.description,
            genre_json = value.genreJson,
            status = value.status,
            thumbnail_url = value.thumbnailUrl,
            favorite = value.favorite,
            date_added = value.dateAdded,
            viewer_flags = value.viewerFlags,
            chapter_flags = value.chapterFlags,
            update_strategy = value.updateStrategy,
            last_modified_at = value.lastModifiedAt,
            favorite_modified_at = value.favoriteModifiedAt,
            excluded_scanlators_json = value.excludedScanlatorsJson,
            version = value.version,
            notes = value.notes,
            initialized = value.initialized,
            memo_json = value.memoJson,
        )
        lastInsertRowId()
    }

    override fun updateManga(value: MangaRecord) {
        queries.updateManga(
            source_id = value.sourceId,
            url = value.url,
            title = value.title,
            artist = value.artist,
            author = value.author,
            description = value.description,
            genre_json = value.genreJson,
            status = value.status,
            thumbnail_url = value.thumbnailUrl,
            favorite = value.favorite,
            date_added = value.dateAdded,
            viewer_flags = value.viewerFlags,
            chapter_flags = value.chapterFlags,
            update_strategy = value.updateStrategy,
            last_modified_at = value.lastModifiedAt,
            favorite_modified_at = value.favoriteModifiedAt,
            excluded_scanlators_json = value.excludedScanlatorsJson,
            version = value.version,
            notes = value.notes,
            initialized = value.initialized,
            memo_json = value.memoJson,
            id = value.id,
        )
    }

    override fun findChapter(mangaId: Long, url: String): ChapterRecord? =
        queries.selectChapterByIdentity(mangaId, url).executeAsOneOrNull()?.toRecord()

    override fun insertChapter(value: ChapterRecord): Long = database.transactionWithResult {
        queries.insertChapter(
            manga_id = value.mangaId,
            url = value.url,
            name = value.name,
            scanlator = value.scanlator,
            read = value.read,
            bookmark = value.bookmark,
            last_page_read = value.lastPageRead,
            date_fetch = value.dateFetch,
            date_upload = value.dateUpload,
            chapter_number = value.chapterNumber,
            source_order = value.sourceOrder,
            last_modified_at = value.lastModifiedAt,
            version = value.version,
            memo_json = value.memoJson,
        )
        lastInsertRowId()
    }

    override fun updateChapter(value: ChapterRecord) {
        queries.updateChapter(
            manga_id = value.mangaId,
            url = value.url,
            name = value.name,
            scanlator = value.scanlator,
            read = value.read,
            bookmark = value.bookmark,
            last_page_read = value.lastPageRead,
            date_fetch = value.dateFetch,
            date_upload = value.dateUpload,
            chapter_number = value.chapterNumber,
            source_order = value.sourceOrder,
            last_modified_at = value.lastModifiedAt,
            version = value.version,
            memo_json = value.memoJson,
            id = value.id,
        )
    }

    override fun upsertCategory(value: CategoryRecord): Long {
        queries.insertCategory(value.name, value.sortOrder, value.flags)
        return checkNotNull(queries.selectCategoryByName(value.name).executeAsOneOrNull()).id
    }

    override fun linkCategory(mangaId: Long, categoryId: Long) {
        queries.linkMangaCategory(mangaId, categoryId)
    }

    override fun upsertHistory(value: HistoryRecord) {
        queries.upsertHistory(value.chapterId, value.lastRead, value.readDuration)
    }

    override fun findTracking(mangaId: Long, trackerId: Long): TrackingRecord? =
        queries.selectTrackingByIdentity(mangaId, trackerId).executeAsOneOrNull()?.toRecord()

    override fun insertTracking(value: TrackingRecord) {
        queries.insertTracking(
            manga_id = value.mangaId,
            tracker_id = value.trackerId,
            remote_id = value.remoteId,
            library_id = value.libraryId,
            title = value.title,
            last_chapter_read = value.lastChapterRead,
            total_chapters = value.totalChapters,
            score = value.score,
            status = value.status,
            started_reading_date = value.startedReadingDate,
            finished_reading_date = value.finishedReadingDate,
            private = value.private,
            tracking_url = value.trackingUrl,
        )
    }

    override fun updateTracking(value: TrackingRecord) {
        queries.updateTracking(
            remote_id = value.remoteId,
            library_id = value.libraryId,
            title = value.title,
            last_chapter_read = value.lastChapterRead,
            total_chapters = value.totalChapters,
            score = value.score,
            status = value.status,
            started_reading_date = value.startedReadingDate,
            finished_reading_date = value.finishedReadingDate,
            private = value.private,
            tracking_url = value.trackingUrl,
            id = value.id,
        )
    }

    override fun upsertSource(value: SourceRecord) {
        queries.upsertSourceMetadata(value.sourceId, value.name, value.importedAt)
    }

    override fun upsertPreference(value: PreferenceSnapshotRecord) {
        queries.upsertPreferenceSnapshot(value.key, value.valueType, value.valueJson, value.importedAt)
    }

    override fun upsertSourcePreference(value: SourcePreferenceSnapshotRecord) {
        queries.upsertSourcePreferenceSnapshot(
            value.sourceKey,
            value.key,
            value.valueType,
            value.valueJson,
            value.importedAt,
        )
    }

    override fun findLocalMangaByManifest(manifestSha256: String): LocalMangaRecord? =
        queries.selectLocalMangaByManifest(manifestSha256).executeAsOneOrNull()?.let { local ->
            LocalMangaRecord(
                mangaId = local.manga_id,
                storagePath = local.storage_path,
                manifestSha256 = local.manifest_sha256,
                importedAt = local.imported_at,
            )
        }

    override fun localMangaStoragePaths(): Set<String> =
        queries.selectLocalMangaStoragePaths().executeAsList().toSet()

    override fun insertLocalManga(value: LocalMangaRecord) {
        queries.insertLocalMangaEntry(value.mangaId, value.storagePath, value.manifestSha256, value.importedAt)
    }

    override fun insertLocalChapter(value: LocalChapterRecord) {
        queries.insertLocalChapterAsset(
            value.chapterId,
            value.relativePath,
            value.assetKind,
            value.sizeBytes,
            value.modifiedAt,
        )
    }

    override fun insertReport(value: ImportReportRecord): Long = database.transactionWithResult {
        queries.insertImportReport(
            import_type = value.importType.name,
            source_path = value.sourcePath,
            status = value.status.name,
            started_at = value.startedAt,
            finished_at = value.finishedAt,
            manga_inserted = value.counts.mangaInserted,
            manga_merged = value.counts.mangaMerged,
            chapters_inserted = value.counts.chaptersInserted,
            chapters_merged = value.counts.chaptersMerged,
            categories_linked = value.counts.categoriesLinked,
            preferences_imported = value.counts.preferencesImported,
            preferences_skipped = value.counts.preferencesSkipped,
        )
        lastInsertRowId()
    }

    override fun insertReportItem(reportId: Long, value: ImportReportItemRecord) {
        queries.insertImportReportItem(
            report_id = reportId,
            item_type = value.itemType,
            item_key = value.itemKey,
            outcome = value.outcome,
            reason = value.reason,
            message = value.message,
        )
    }

    override fun close() {
        driver.close()
    }

    private fun lastInsertRowId(): Long = queries.lastInsertRowId().executeAsOne()
}

private data class ReaderProgressVersion(
    val generation: Long,
    val sequence: Long,
) : Comparable<ReaderProgressVersion> {
    override fun compareTo(other: ReaderProgressVersion): Int =
        compareValuesBy(this, other, ReaderProgressVersion::generation, ReaderProgressVersion::sequence)
}

private fun validateReaderProgress(update: ReaderProgressUpdate) {
    require(update.chapterId >= 0) { "chapterId must not be negative" }
    require(update.pageIndex in 0..Int.MAX_VALUE.toLong()) { "pageIndex must be between 0 and Int.MAX_VALUE" }
    require(update.lastReadEpochMillis >= 0) { "lastReadEpochMillis must not be negative" }
    require(update.readDurationDeltaMillis in 0..86_400_000L) {
        "readDurationDeltaMillis must be between 0 and 86400000"
    }
    require(update.generation >= 0) { "generation must not be negative" }
    require(update.sequence >= 0) { "sequence must not be negative" }
}

private fun SelectReaderChapterAsset.toReaderChapterAsset(): ReaderChapterAsset? = readerChapterAsset(
    manga_id,
    chapter_id,
    manga_title,
    chapter_name,
    storage_path,
    relative_path,
    asset_kind,
    size_bytes,
    modified_at,
    last_page_read,
    read,
)

private fun SelectPreviousReaderChapterAsset.toReaderChapterAsset(): ReaderChapterAsset? = readerChapterAsset(
    manga_id,
    chapter_id,
    manga_title,
    chapter_name,
    storage_path,
    relative_path,
    asset_kind,
    size_bytes,
    modified_at,
    last_page_read,
    read,
)

private fun SelectNextReaderChapterAsset.toReaderChapterAsset(): ReaderChapterAsset? = readerChapterAsset(
    manga_id,
    chapter_id,
    manga_title,
    chapter_name,
    storage_path,
    relative_path,
    asset_kind,
    size_bytes,
    modified_at,
    last_page_read,
    read,
)

private fun readerChapterAsset(
    mangaId: Long,
    chapterId: Long,
    mangaTitle: String,
    chapterName: String,
    storagePath: String,
    relativePath: String,
    assetKind: String,
    sizeBytes: Long,
    modifiedAt: Long,
    lastPageRead: Long,
    read: Boolean,
): ReaderChapterAsset? = runCatching {
    val storageRoot = Path.of(storagePath).toAbsolutePath().normalize()
    val storedRelativePath = Path.of(relativePath)
    if (storedRelativePath.isAbsolute) return null
    val resolvedPath = storageRoot.resolve(storedRelativePath).normalize()
    if (!resolvedPath.startsWith(storageRoot)) return null
    ReaderChapterAsset(
        mangaId = mangaId,
        chapterId = chapterId,
        mangaTitle = mangaTitle,
        chapterName = chapterName,
        storageRoot = storageRoot,
        relativePath = storageRoot.relativize(resolvedPath),
        assetKind = assetKind,
        sizeBytes = sizeBytes,
        modifiedAt = modifiedAt,
        lastPageRead = lastPageRead,
        read = read,
    )
}.getOrNull()

private fun SelectLibrary.toModel() = LibraryManga(
    id = id,
    sourceId = source_id,
    url = url,
    title = title,
    thumbnailUrl = thumbnail_url,
    chapterCount = chapter_count,
    unreadCount = unread_count,
    author = author,
)

private fun Manga.toRecord() = MangaRecord(
    id = id,
    sourceId = source_id,
    url = url,
    title = title,
    artist = artist,
    author = author,
    description = description,
    genreJson = genre_json,
    status = status,
    thumbnailUrl = thumbnail_url,
    favorite = favorite,
    dateAdded = date_added,
    viewerFlags = viewer_flags,
    chapterFlags = chapter_flags,
    updateStrategy = update_strategy,
    lastModifiedAt = last_modified_at,
    favoriteModifiedAt = favorite_modified_at,
    excludedScanlatorsJson = excluded_scanlators_json,
    version = version,
    notes = notes,
    initialized = initialized,
    memoJson = memo_json,
)

private fun Manga.toDetails(categories: List<CategoryRecord>) = MangaDetails(
    id = id,
    sourceId = source_id,
    url = url,
    title = title,
    artist = artist,
    author = author,
    description = description,
    genreJson = genre_json,
    status = status,
    thumbnailUrl = thumbnail_url,
    favorite = favorite,
    dateAdded = date_added,
    viewerFlags = viewer_flags,
    chapterFlags = chapter_flags,
    updateStrategy = update_strategy,
    lastModifiedAt = last_modified_at,
    favoriteModifiedAt = favorite_modified_at,
    excludedScanlatorsJson = excluded_scanlators_json,
    version = version,
    notes = notes,
    initialized = initialized,
    memoJson = memo_json,
    categories = categories,
)

private fun Chapter.toRecord() = ChapterRecord(
    id = id,
    mangaId = manga_id,
    url = url,
    name = name,
    scanlator = scanlator,
    read = read,
    bookmark = bookmark,
    lastPageRead = last_page_read,
    dateFetch = date_fetch,
    dateUpload = date_upload,
    chapterNumber = chapter_number,
    sourceOrder = source_order,
    lastModifiedAt = last_modified_at,
    version = version,
    memoJson = memo_json,
)

private fun Chapter.toModel() = LibraryChapter(
    id = id,
    mangaId = manga_id,
    url = url,
    name = name,
    scanlator = scanlator,
    read = read,
    bookmark = bookmark,
    lastPageRead = last_page_read,
    dateFetch = date_fetch,
    dateUpload = date_upload,
    chapterNumber = chapter_number,
    sourceOrder = source_order,
    lastModifiedAt = last_modified_at,
    version = version,
    memoJson = memo_json,
)

private fun Category.toRecord() = CategoryRecord(id, name, sort_order, flags)

private fun Tracking.toRecord() = TrackingRecord(
    id = id,
    mangaId = manga_id,
    trackerId = tracker_id,
    remoteId = remote_id,
    libraryId = library_id,
    title = title,
    lastChapterRead = last_chapter_read,
    totalChapters = total_chapters,
    score = score,
    status = status,
    startedReadingDate = started_reading_date,
    finishedReadingDate = finished_reading_date,
    private = private_,
    trackingUrl = tracking_url,
)

private fun Import_report.toModel(items: List<ImportReportItem>) = ImportReport(
    id = id,
    importType = ImportType.valueOf(import_type),
    sourcePath = source_path,
    status = ImportStatus.valueOf(status),
    startedAt = started_at,
    finishedAt = finished_at,
    counts = ImportCounts(
        mangaInserted = manga_inserted,
        mangaMerged = manga_merged,
        chaptersInserted = chapters_inserted,
        chaptersMerged = chapters_merged,
        categoriesLinked = categories_linked,
        preferencesImported = preferences_imported,
        preferencesSkipped = preferences_skipped,
    ),
    items = items,
)

private fun Import_report_item.toModel() = ImportReportItem(
    itemType = item_type,
    itemKey = item_key,
    outcome = outcome,
    reason = reason,
    message = message,
    id = id,
)
