package eu.kanade.tachiyomi.data.recommendation

/**
 * Source-scoped, in-memory history of recently shown recommendation identities.
 *
 * Only opaque work fingerprints are retained. Manga/card instances must stay outside this store so
 * an exposure from an earlier request can never become recommendation content for a later request.
 */
internal class RecommendationExposureStore(
    private val now: () -> Long = System::currentTimeMillis,
    private val maxEntriesPerSource: Int = DEFAULT_MAX_ENTRIES_PER_SOURCE,
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
) {

    private val lock = Any()
    private val exposuresByTarget = mutableMapOf<ExposureScope, MutableList<ExposureEntry>>()
    private var nextSequence = 0L

    init {
        require(maxEntriesPerSource > 0) { "maxEntriesPerSource must be positive" }
        require(ttlMillis > 0) { "ttlMillis must be positive" }
    }

    /** Returns an immutable, oldest-first view of the source's unexpired exposure history. */
    fun snapshot(
        sourceId: Long,
        targetKey: String = SOURCE_WIDE_TARGET_KEY,
    ): RecommendationExposureSnapshot = synchronized(lock) {
        pruneExpiredLocked(now())
        RecommendationExposureSnapshot(
            exposures = exposuresByTarget[ExposureScope(sourceId, targetKey)]
                ?.map { exposure ->
                    RecommendationExposure(
                        workFingerprints = exposure.workFingerprints.toSet(),
                        shownAtMillis = exposure.shownAtMillis,
                        sequence = exposure.sequence,
                    )
                }
                .orEmpty(),
        )
    }

    /**
     * Records one display batch in display order. Repeated keys become the most recently shown and
     * blank keys are ignored.
     */
    fun recordShown(
        sourceId: Long,
        workFingerprints: Iterable<String>,
        targetKey: String = SOURCE_WIDE_TARGET_KEY,
    ) {
        recordShownWorks(sourceId, workFingerprints.map(::setOf), targetKey)
    }

    /** Records complete alias sets while counting each set as one recently shown work. */
    fun recordShownWorks(
        sourceId: Long,
        works: Iterable<Set<String>>,
        targetKey: String = SOURCE_WIDE_TARGET_KEY,
    ) = synchronized(lock) {
        val recordedAt = now()
        pruneExpiredLocked(recordedAt)

        val sourceExposures = exposuresByTarget.getOrPut(ExposureScope(sourceId, targetKey), ::mutableListOf)
        works.forEach { rawFingerprints ->
            val fingerprints = rawFingerprints.filterTo(linkedSetOf(), String::isNotBlank)
            if (fingerprints.isEmpty()) return@forEach
            val previousAliases = sourceExposures.asSequence()
                .filter { it.workFingerprints.any(fingerprints::contains) }
                .flatMap { it.workFingerprints.asSequence() }
                .toSet()
            sourceExposures.removeAll { it.workFingerprints.any(fingerprints::contains) }
            sourceExposures += ExposureEntry(
                workFingerprints = previousAliases + fingerprints,
                shownAtMillis = recordedAt,
                sequence = nextSequence++,
            )
        }
        while (sourceExposures.size > maxEntriesPerSource) {
            sourceExposures.removeAt(0)
        }
    }

    private fun pruneExpiredLocked(currentTimeMillis: Long) {
        val sourceIterator = exposuresByTarget.iterator()
        while (sourceIterator.hasNext()) {
            val sourceExposures = sourceIterator.next().value
            sourceExposures.removeAll { currentTimeMillis - it.shownAtMillis >= ttlMillis }
            if (sourceExposures.isEmpty()) {
                sourceIterator.remove()
            }
        }
    }

    private companion object {
        const val DEFAULT_MAX_ENTRIES_PER_SOURCE = 40
        const val DEFAULT_TTL_MILLIS = 30 * 60 * 1000L
        const val SOURCE_WIDE_TARGET_KEY = "__source_wide__"
    }

    private data class ExposureScope(val sourceId: Long, val targetKey: String)

    private data class ExposureEntry(
        val workFingerprints: Set<String>,
        val shownAtMillis: Long,
        val sequence: Long,
    )
}

/** One immutable exposure record, ordered by its real display time and a same-tick sequence. */
internal data class RecommendationExposure(
    val workFingerprints: Set<String>,
    val shownAtMillis: Long,
    val sequence: Long,
)

/** A stable exposure view that is unaffected by later writes to the store. */
internal data class RecommendationExposureSnapshot(
    val exposures: List<RecommendationExposure>,
) {
    val leastRecentlyShownWorksFirst: List<Set<String>> = exposures.map(RecommendationExposure::workFingerprints)

    val recentKeys: Set<String> = exposures.flatMap(RecommendationExposure::workFingerprints).toSet()

    fun exposureIndex(workFingerprints: Set<String>): Int? {
        return exposures.indexOfFirst { exposure ->
            exposure.workFingerprints.any(workFingerprints::contains)
        }.takeIf { it >= 0 }
    }

    fun lastShownExposure(workFingerprints: Set<String>): RecommendationExposure? {
        return exposures.asSequence()
            .filter { exposure -> exposure.workFingerprints.any(workFingerprints::contains) }
            .maxWithOrNull(
                compareBy<RecommendationExposure>(RecommendationExposure::shownAtMillis)
                    .thenBy(RecommendationExposure::sequence),
            )
    }
}
