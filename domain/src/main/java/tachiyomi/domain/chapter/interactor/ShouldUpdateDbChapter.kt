package tachiyomi.domain.chapter.interactor

import tachiyomi.domain.chapter.model.Chapter
import kotlin.math.abs
import kotlin.math.ulp

class ShouldUpdateDbChapter {

    fun await(dbChapter: Chapter, sourceChapter: Chapter): Boolean {
        return dbChapter.scanlator != sourceChapter.scanlator ||
            dbChapter.name != sourceChapter.name ||
            dbChapter.dateUpload != sourceChapter.dateUpload ||
            abs(dbChapter.chapterNumber - sourceChapter.chapterNumber) > 1e-9 ||
            dbChapter.sourceOrder != sourceChapter.sourceOrder
    }
}
