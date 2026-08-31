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
    fun linkCategory(mangaId: Long, categoryId: Long)
    fun upsertHistory(value: HistoryRecord)
    fun findTracking(mangaId: Long, trackerId: Long): TrackingRecord?
    fun insertTracking(value: TrackingRecord)
    fun updateTracking(value: TrackingRecord)
    fun upsertSource(value: SourceRecord)
    fun upsertPreference(value: PreferenceSnapshotRecord)
    fun upsertSourcePreference(value: SourcePreferenceSnapshotRecord)
    fun insertLocalManga(value: LocalMangaRecord)
    fun insertLocalChapter(value: LocalChapterRecord)
    fun insertReport(value: ImportReportRecord): Long
    fun insertReportItem(reportId: Long, value: ImportReportItemRecord)
}
