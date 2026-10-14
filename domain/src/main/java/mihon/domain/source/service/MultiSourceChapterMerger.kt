package mihon.domain.source.service

import eu.kanade.tachiyomi.source.model.SChapter
import mihon.domain.source.model.ChapterOrigin
import mihon.domain.source.model.assignedSourceId
import mihon.domain.source.model.origins
import mihon.domain.source.model.withAssignedSource
import mihon.domain.source.model.withChapterOrigins
import tachiyomi.domain.chapter.model.Chapter

/** One stable local chapter per recognized number; unknown chapters and specials stay source-specific. */
object MultiSourceChapterMerger {
    private val separateChapter =
        Regex("(?i)\\b(special|extra|bonus|prologue|epilogue|volume|vol\\.?|season|v\\d+|s\\d+)\\b")

    private fun key(number: Double, name: String, source: Long, url: String): String =
        if (number.isFinite() && number >= 0 && !separateChapter.containsMatchIn(name)) {
            "number:${number.toFloat()}"
        } else {
            "source:$source:$url"
        }

    fun identity(chapter: Chapter, primarySourceId: Long): String {
        val origin = chapter.origins().firstOrNull()
        return key(
            chapter.chapterNumber,
            origin?.name ?: chapter.name,
            origin?.sourceId ?: primarySourceId,
            origin?.url ?: chapter.url,
        )
    }

    fun merge(
        primarySourceId: Long,
        sourceChapters: List<Pair<Long, List<SChapter>>>,
        existing: List<Chapter>,
        protectedIds: Set<Long>,
        previouslyQueuedOrDownloadedIds: Set<Long> = emptySet(),
    ): List<SChapter> {
        val oldByKey = existing.groupBy { chapter ->
            identity(chapter, primarySourceId)
        }
        val candidates = sourceChapters.flatMap { (source, chapters) ->
            chapters.distinctBy { it.url }.map { ChapterOrigin.from(source, it) }
        }.groupBy { key(it.chapterNumber.toDouble(), it.name, it.sourceId, it.url) }.toMutableMap()

        // Never discard downloaded/read chapters merely because a source no longer lists them.
        oldByKey.forEach { (key, chapters) ->
            if (key !in candidates && chapters.any { it.id in protectedIds }) {
                candidates[key] = chapters.first { it.id in protectedIds }.let { chapter ->
                    chapter.origins().ifEmpty {
                        listOf(
                            ChapterOrigin(
                                primarySourceId,
                                chapter.url,
                                chapter.name,
                                chapter.scanlator,
                                chapter.dateUpload?.toEpochMilliseconds() ?: 0,
                                chapter.chapterNumber.toFloat(),
                                chapter.memo,
                            ),
                        )
                    }
                }
            }
        }
        return candidates.entries.sortedWith(compareBy({ it.value.first().chapterNumber }, { it.key }))
            .map { (key, alternatives) ->
                val old = oldByKey[key]?.maxByOrNull {
                    (if (it.lastPageRead > 0) 16 else 0) + (if (it.read) 8 else 0) +
                        (if (it.id in protectedIds) 4 else 0) + (if (it.bookmark) 1 else 0)
                }
                val pinned = old?.takeIf { it.id in protectedIds }?.let { chapter ->
                    chapter.origins().firstOrNull() ?: ChapterOrigin(
                        primarySourceId,
                        chapter.url,
                        chapter.name,
                        chapter.scanlator,
                        chapter.dateUpload?.toEpochMilliseconds() ?: 0,
                        chapter.chapterNumber.toFloat(),
                        chapter.memo,
                    )
                }
                val previous = old?.origins()?.firstOrNull()?.let { origin ->
                    alternatives.firstOrNull { it.sourceId == origin.sourceId && it.url == origin.url }
                }
                // Choose representative metadata, not a download provider. Queue planning balances providers later.
                val selected = pinned ?: previous ?: alternatives.firstOrNull { it.sourceId == primarySourceId }
                    ?: alternatives.minBy { it.sourceId }
                val assignedSource = if (old != null &&
                    (old.assignedSourceId() != null || old.id in previouslyQueuedOrDownloadedIds)
                ) {
                    selected.sourceId
                } else {
                    null
                }
                val origins = (listOf(selected) + alternatives).distinctBy { it.sourceId }
                selected.toSChapter().also { chapter ->
                    // Keep local identity and storage names stable when choosing another provider.
                    chapter.url = old?.url ?: if (selected.sourceId == primarySourceId) selected.url else "mihon://$key"
                    chapter.name = old?.name ?: selected.name
                    chapter.scanlator = if (old != null) old.scanlator else selected.scanlator
                    chapter.memo = selected.memo.withChapterOrigins(origins).withAssignedSource(assignedSource)
                }
            }.asReversed()
    }
}
