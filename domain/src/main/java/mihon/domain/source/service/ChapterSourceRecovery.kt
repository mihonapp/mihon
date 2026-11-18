package mihon.domain.source.service

import mihon.domain.source.model.origins
import mihon.domain.source.model.withAssignedSource
import mihon.domain.source.model.withChapterOrigins
import tachiyomi.domain.chapter.model.Chapter

/** Select an untried installed provider without losing alternatives or changing local chapter identity. */
object ChapterSourceRecovery {
    fun recover(
        chapter: Chapter,
        installedSourceIds: Set<Long>,
        attemptedSourceIds: Set<Long>,
        unavailableSourceIds: Set<Long> = emptySet(),
    ): Chapter? {
        if (!chapter.read && chapter.lastPageRead > 0) return null
        val origins = chapter.origins()
        val candidates = origins.filter { it.sourceId in installedSourceIds && it.sourceId !in attemptedSourceIds }
        val next = candidates.firstOrNull { it.sourceId !in unavailableSourceIds } ?: candidates.firstOrNull()
            ?: return null
        return chapter.copy(
            memo = chapter.memo.withChapterOrigins(listOf(next) + origins.filterNot { it == next })
                .withAssignedSource(next.sourceId),
        )
    }
}

/** A transient outage should not send every subsequent chapter to the same failing provider. */
class SourceRecoveryState(
    private val cooldownMillis: Long = 60_000,
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private val unavailableUntil = mutableMapOf<Long, Long>()

    @Synchronized
    fun failed(sourceId: Long) {
        unavailableUntil[sourceId] = nowMillis() + cooldownMillis
    }

    @Synchronized
    fun succeeded(sourceId: Long) {
        unavailableUntil.remove(sourceId)
    }

    @Synchronized
    fun unavailableSources(): Set<Long> {
        val now = nowMillis()
        unavailableUntil.entries.removeAll { it.value <= now }
        return unavailableUntil.keys.toSet()
    }
}
