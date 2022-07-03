package eu.kanade.domain.chapter.model

import eu.kanade.tachiyomi.data.database.models.ChapterImpl
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.data.database.models.Chapter as DbChapter

data class Chapter(
    val id: Long,
    val mangaId: Long,
    val read: Boolean,
    val bookmark: Boolean,
    val lastPageRead: Long,
    val dateFetch: Long,
    val sourceOrder: Long,
    val url: String,
    val name: String,
    val dateUpload: Long,
    val chapterNumber: Float,
    val scanlator: String?,
) {
    val isRecognizedNumber: Boolean
        get() = chapterNumber >= 0f

    fun toSChapter(): SChapter {
        return SChapter.create().also {
            it.url = url
            it.name = name
            it.date_upload = dateUpload
            it.chapter_number = chapterNumber
            it.scanlator = scanlator
        }
    }

    fun copyFromSChapter(sChapter: SChapter): Chapter {
        return this.copy(
            name = sChapter.name,
            url = sChapter.url,
            dateUpload = sChapter.date_upload,
            chapterNumber = sChapter.chapter_number,
            scanlator = sChapter.scanlator,
        )
    }

    companion object {
        fun create() = Chapter(
            id = -1,
            mangaId = -1,
            read = false,
            bookmark = false,
            lastPageRead = 0,
            dateFetch = 0,
            sourceOrder = 0,
            url = "",
            name = "",
            dateUpload = -1,
            chapterNumber = -1f,
            scanlator = null,
        )
    }
}

// TODO: Remove when all deps are migrated
fun Chapter.toDbChapter(): DbChapter = ChapterImpl().also {
    it.id = id
    it.manga_id = mangaId
    it.url = url
    it.name = name
    it.scanlator = scanlator
    it.read = read
    it.bookmark = bookmark
    it.last_page_read = lastPageRead.toInt()
    it.date_fetch = dateFetch
    it.date_upload = dateUpload
    it.chapter_number = chapterNumber
    it.source_order = sourceOrder.toInt()
}
