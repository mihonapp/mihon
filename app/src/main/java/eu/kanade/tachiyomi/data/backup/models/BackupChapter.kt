package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import mihon.core.common.extensions.JsonObjectEmptyBytes
import mihon.core.common.extensions.toByteArray
import mihon.core.common.extensions.toJsonObject
import tachiyomi.domain.chapter.model.Chapter

@Serializable
class BackupChapter(
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
    // @ProtoNumber(11) var lastModifiedAt: Long, artifact of the abandoned sync attempt
    // @ProtoNumber(12) var version: Long, artifact of the abandoned sync attempt
    @ProtoNumber(13) var memo: ByteArray = JsonObjectEmptyBytes,
    /**
     * When the reading state was decided on the device that wrote this, in seconds, so a correction
     * (marking chapters unread, or going back to an earlier one) can reach the other devices. Only
     * the sync writes it. Numbered far from the upstream fields so it can never collide with one
     * added there.
     */
    @ProtoNumber(500) var readModifiedAt: Long = 0,
) {
    fun toChapterImpl(): Chapter {
        return Chapter.create().copy(
            url = this@BackupChapter.url,
            name = this@BackupChapter.name,
            chapterNumber = this@BackupChapter.chapterNumber.toDouble(),
            scanlator = this@BackupChapter.scanlator,
            read = this@BackupChapter.read,
            bookmark = this@BackupChapter.bookmark,
            lastPageRead = this@BackupChapter.lastPageRead,
            dateFetch = this@BackupChapter.dateFetch,
            dateUpload = this@BackupChapter.dateUpload,
            sourceOrder = this@BackupChapter.sourceOrder,
            memo = this@BackupChapter.memo.toJsonObject(),
        )
    }
}

fun Chapter.toBackupChapter() = BackupChapter(
    url = url,
    name = name,
    chapterNumber = chapterNumber.toFloat(),
    scanlator = scanlator,
    read = read,
    bookmark = bookmark,
    lastPageRead = lastPageRead,
    dateFetch = dateFetch,
    dateUpload = dateUpload,
    sourceOrder = sourceOrder,
    memo = memo.toByteArray(),
)
