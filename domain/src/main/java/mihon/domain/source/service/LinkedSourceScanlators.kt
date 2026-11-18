package mihon.domain.source.service

import eu.kanade.tachiyomi.source.model.SChapter
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.service.ChapterRecognition

object LinkedSourceScanlators {
    data class Summary(val chapterCount: Int, val firstChapter: Double?, val lastChapter: Double?)

    // A blank entry represents unnamed releases and must remain selectable alongside named groups.
    fun available(chapters: List<SChapter>): Set<String> = chapters.map { it.scanlator.orEmpty().trim() }
        .distinctBy { it.lowercase() }
        .sortedWith(String.CASE_INSENSITIVE_ORDER)
        .toSet()

    fun summaries(title: String, chapters: List<SChapter>): Map<String, Summary> {
        val recognized = ChapterRecognition.parseChapterNumbers(
            title,
            chapters.map { Chapter.create().copy(name = it.name) },
        )
        val numbered = chapters.zip(recognized).map { (chapter, numbering) ->
            val number = chapter.chapter_number.takeIf { it.isFinite() && it >= 0 }?.toString()?.toDouble()
                ?: numbering.chapter?.let(ChapterRecognition::toDouble)
            chapter.scanlator.orEmpty().trim().lowercase() to number
        }.groupBy({ it.first }, { it.second })
        return available(chapters).associateWith { scanlator ->
            val entries = numbered.getValue(scanlator.lowercase())
            val numbers = entries.filterNotNull()
            Summary(entries.size, numbers.minOrNull(), numbers.maxOrNull())
        }
    }
}
