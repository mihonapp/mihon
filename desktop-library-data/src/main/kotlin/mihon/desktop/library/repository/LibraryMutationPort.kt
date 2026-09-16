package mihon.desktop.library.repository

import mihon.desktop.library.model.CategoryRecord
import mihon.desktop.library.model.ChapterRecord
import mihon.desktop.library.model.HistoryRecord
import mihon.desktop.library.model.ImportReportItemRecord
import mihon.desktop.library.model.ImportReportRecord
import mihon.desktop.library.model.LocalChapterRecord
import mihon.desktop.library.model.LocalMangaRecord
import mihon.desktop.library.model.MangaRecord
import mihon.desktop.library.model.PreferenceSnapshotRecord
import mihon.desktop.library.model.SourcePreferenceSnapshotRecord
import mihon.desktop.library.model.SourceRecord
import mihon.desktop.library.model.TrackingRecord

interface LibraryMutationPort {
    fun <T> transaction(block: LibraryMutationPort.() -> T): T
    fun findManga(sourceId: Long, url: String): MangaRecord?
    fun insertManga(value: MangaRecord): Long
    fun updateManga(value: MangaRecord)
    fun findChapter(mangaId: Long, url: String): ChapterRecord?
    fun insertChapter(value: ChapterRecord): Long
    fun updateChapter(value: ChapterRecord)
    fun upsertCategory(value: CategoryRecord): Long
    fun deleteCategory(categoryId: Long)
    fun updateCategoryName(categoryId: Long, name: String)
    fun updateCategoryOrder(categoryId: Long, sortOrder: Long)
    fun linkCategory(mangaId: Long, categoryId: Long)
    fun unlinkCategory(mangaId: Long, categoryId: Long)
    fun setMangaCategories(mangaId: Long, categoryIds: List<Long>)
    fun upsertHistory(value: HistoryRecord)
    fun deleteHistory(chapterId: Long)
    fun clearAllHistory()
    fun findTracking(mangaId: Long, trackerId: Long): TrackingRecord?
    fun insertTracking(value: TrackingRecord)
    fun updateTracking(value: TrackingRecord)
    fun deleteTracking(mangaId: Long, trackerId: Long)
    fun upsertSource(value: SourceRecord)
    fun upsertPreference(value: PreferenceSnapshotRecord)
    fun upsertSourcePreference(value: SourcePreferenceSnapshotRecord)
    fun findLocalMangaByManifest(manifestSha256: String): LocalMangaRecord?
    fun localMangaStoragePaths(): Set<String>

    /** Paths retained by local-import cleanup; online downloads have separate ownership. */
    fun importedMangaStoragePaths(): Set<String> = localMangaStoragePaths()
    fun insertLocalManga(value: LocalMangaRecord)
    fun insertLocalChapter(value: LocalChapterRecord)

    /** True only when both the manga root and chapter asset match the published download. */
    fun isLocalChapterAssetRegistered(
        mangaId: Long,
        storagePath: String,
        chapterId: Long,
        relativePath: String,
        sizeBytes: Long,
    ): Boolean = false

    /** Removes the chapter asset and its manga root when no other local assets use that root. */
    fun deleteLocalChapterAsset(mangaId: Long, chapterId: Long) = Unit
    fun insertReport(value: ImportReportRecord): Long
    fun insertReportItem(reportId: Long, value: ImportReportItemRecord)
}
