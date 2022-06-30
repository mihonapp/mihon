package eu.kanade.tachiyomi.data.backup.full.models

import eu.kanade.tachiyomi.data.database.models.ChapterImpl
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class BackupChapter(
    // in 1.x some of these values have different names
    // url is called key in 1.x
    @ProtoNumber(1) var url: String,
    @ProtoNumber(2) var name: String,
    @ProtoNumber(3) var scanlator: String? = null,
    @ProtoNumber(4) var read: Boolean = false,
    @ProtoNumber(5) var bookmark: Boolean = false,
    // lastPageRead is called progress in 1.x
    @ProtoNumber(6) var lastPageRead: Long = 0,
    @ProtoNumber(7) var dateFetch: Long = 0,
    @ProtoNumber(8) var dateUpload: Long = 0,
    // chapterNumber is called number is 1.x
    @ProtoNumber(9) var chapterNumber: Float = 0F,
    @ProtoNumber(10) var sourceOrder: Long = 0,
) {
    fun toChapterImpl(): ChapterImpl {
        return ChapterImpl().apply {
            url = this@BackupChapter.url
            name = this@BackupChapter.name
            chapter_number = this@BackupChapter.chapterNumber
            scanlator = this@BackupChapter.scanlator
            read = this@BackupChapter.read
            bookmark = this@BackupChapter.bookmark
            last_page_read = this@BackupChapter.lastPageRead.toInt()
            date_fetch = this@BackupChapter.dateFetch
            date_upload = this@BackupChapter.dateUpload
            source_order = this@BackupChapter.sourceOrder.toInt()
        }
    }
}

val backupChapterMapper = {
        _: Long,
        _: Long,
        url: String,
        name: String,
        scanlator: String?,
        read: Boolean,
        bookmark: Boolean,
        lastPageRead: Long,
        chapterNumber: Float,
        source_order: Long,
        dateFetch: Long,
        dateUpload: Long,  ->
    BackupChapter(
        url = url,
        name = name,
        chapterNumber = chapterNumber,
        scanlator = scanlator,
        read = read,
        bookmark = bookmark,
        lastPageRead = lastPageRead,
        dateFetch = dateFetch,
        dateUpload = dateUpload,
        sourceOrder = source_order,
    )
}
