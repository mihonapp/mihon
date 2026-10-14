package mihon.domain.source.service

import mihon.domain.source.model.origins
import mihon.domain.source.model.sourceId
import mihon.domain.source.model.withAssignedSource
import mihon.domain.source.model.withChapterOrigins
import tachiyomi.domain.chapter.model.Chapter

/** Balance the requested batch, including existing queue load, without switching a partially read version. */
object ChapterDownloadPlanner {
    fun plan(
        chapters: List<Chapter>,
        defaultSourceId: Long,
        installedSourceIds: Set<Long>,
        queuedCounts: Map<Long, Int>,
        unavailableSourceIds: Set<Long> = emptySet(),
    ): List<Chapter> {
        val assigned = queuedCounts.toMutableMap()
        return chapters.mapNotNull { chapter ->
            val origins = chapter.origins()
            val planned = if (origins.isEmpty() || (!chapter.read && chapter.lastPageRead > 0)) {
                chapter.takeIf { it.sourceId(defaultSourceId) in installedSourceIds }?.let {
                    if (origins.isEmpty()) {
                        it
                    } else {
                        it.copy(
                            memo = it.memo.withAssignedSource(it.sourceId(defaultSourceId)),
                        )
                    }
                }
            } else {
                val installed = origins.filter { it.sourceId in installedSourceIds }
                // Keep single-provider chapters eligible; cooldown only redirects when another provider exists.
                val healthy = installed.filter { it.sourceId !in unavailableSourceIds }
                val selected = healthy.ifEmpty { installed }
                    .minWithOrNull(compareBy({ assigned[it.sourceId] ?: 0 }, { it.sourceId }))
                    ?: return@mapNotNull null
                chapter.copy(
                    memo = chapter.memo.withChapterOrigins(
                        (listOf(selected) + origins).distinctBy { it.sourceId to it.url },
                    ).withAssignedSource(selected.sourceId),
                )
            } ?: return@mapNotNull null
            val sourceId = planned.sourceId(defaultSourceId)
            assigned[sourceId] = (assigned[sourceId] ?: 0) + 1
            planned
        }
    }
}
