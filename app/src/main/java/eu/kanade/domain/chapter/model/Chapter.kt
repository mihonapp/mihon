package eu.kanade.domain.chapter.model

import eu.kanade.tachiyomi.source.model.SChapter
import mihon.core.common.extensions.toEpochMillisOrZero
import mihon.core.common.extensions.toInstantOrNull
import tachiyomi.domain.chapter.model.Chapter

fun Chapter.toSChapter(): SChapter {
    return SChapter.create().also {
        it.url = url
        it.name = name
        it.date_upload = dateUpload.toEpochMillisOrZero()
        it.chapter_number = chapterNumber.toFloat()
        it.scanlator = scanlator
        it.memo = memo
    }
}

fun Chapter.copyFromSChapter(sChapter: SChapter): Chapter {
    return this.copy(
        name = sChapter.name,
        url = sChapter.url,
        dateUpload = sChapter.date_upload.toInstantOrNull(),
        chapterNumber = sChapter.chapter_number.toDouble(),
        scanlator = sChapter.scanlator?.ifBlank { null }?.trim(),
        memo = sChapter.memo,
    )
}
