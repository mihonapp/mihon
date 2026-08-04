package eu.kanade.tachiyomi.data.recommendation

/** Lightweight, process-local exposure history. It never retains manga or source objects. */
internal class RecommendationExposureStore(
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val capacity: Int = DEFAULT_CAPACITY,
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
) {
    private val buckets = mutableMapOf<BucketKey, MutableList<ExposureRecord>>()
    private var nextSequence = 0L

    init {
        require(capacity > 0)
        require(ttlMillis > 0L)
    }

    @Synchronized
    fun snapshot(sourceId: Long, targetKey: String): RecommendationExposureSnapshot {
        val now = nowMillis()
        val records = activeRecords(BucketKey(sourceId, targetKey), now)
        val exposures = buildMap<String, RecommendationExposure> {
            records.forEach { record ->
                record.identityKeys.forEach { key ->
                    val previous = get(key)
                    if (previous == null || previous.sequence < record.sequence) {
                        put(key, RecommendationExposure(record.shownAtMillis, record.sequence))
                    }
                }
            }
        }
        return RecommendationExposureSnapshot(exposures)
    }

    @Synchronized
    fun record(
        sourceId: Long,
        targetKey: String,
        cards: Collection<RecommendationCard>,
    ) {
        val now = nowMillis()
        val key = BucketKey(sourceId, targetKey)
        val records = activeRecords(key, now).toMutableList()
        cards.forEach { card ->
            if (card.sourceId != sourceId) return@forEach
            val identityKeys = card.identity.exposureKeys.ifEmpty { setOf(card.identity.exposureKey) }
            records.removeAll { existing -> existing.identityKeys.any(identityKeys::contains) }
            records += ExposureRecord(identityKeys, now, nextSequence++)
        }
        while (records.size > capacity) records.removeAt(0)
        if (records.isEmpty()) buckets.remove(key) else buckets[key] = records
    }

    @Synchronized
    fun clear(sourceId: Long, targetKey: String) {
        buckets.remove(BucketKey(sourceId, targetKey))
    }

    @Synchronized
    fun clearSource(sourceId: Long) {
        buckets.keys.removeAll { it.sourceId == sourceId }
    }

    private fun activeRecords(key: BucketKey, now: Long): List<ExposureRecord> {
        val records = buckets[key] ?: return emptyList()
        records.removeAll { now - it.shownAtMillis >= ttlMillis }
        if (records.isEmpty()) buckets.remove(key)
        return records
    }

    private data class BucketKey(val sourceId: Long, val targetKey: String)

    private data class ExposureRecord(
        val identityKeys: Set<String>,
        val shownAtMillis: Long,
        val sequence: Long,
    )

    internal companion object {
        const val DEFAULT_CAPACITY = 40
        const val DEFAULT_TTL_MILLIS = 30 * 60 * 1_000L
    }
}

internal data class RecommendationExposure(
    val shownAtMillis: Long,
    val sequence: Long,
)

internal data class RecommendationExposureSnapshot(
    private val exposures: Map<String, RecommendationExposure>,
) {
    fun wasShown(identityKeys: Set<String>): Boolean = identityKeys.any(exposures::containsKey)

    fun lastShown(identityKeys: Set<String>): RecommendationExposure? {
        return identityKeys.mapNotNull(exposures::get)
            .maxWithOrNull(compareBy(RecommendationExposure::shownAtMillis, RecommendationExposure::sequence))
    }

    internal companion object {
        val EMPTY = RecommendationExposureSnapshot(emptyMap())
    }
}
