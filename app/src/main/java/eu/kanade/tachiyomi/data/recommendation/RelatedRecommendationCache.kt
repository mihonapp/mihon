package eu.kanade.tachiyomi.data.recommendation

import eu.kanade.tachiyomi.source.model.SManga

/** Small target-scoped LRU. It never shares cards between galleries or source IDs. */
internal class RelatedRecommendationCache(
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val freshTtlMillis: Long = DEFAULT_FRESH_TTL_MILLIS,
    private val staleTtlMillis: Long = DEFAULT_STALE_TTL_MILLIS,
    private val negativeTtlMillis: Long = DEFAULT_NEGATIVE_TTL_MILLIS,
) {

    private val entries = LinkedHashMap<String, Entry>(16, 0.75f, true)

    init {
        require(maxEntries > 0)
        require(freshTtlMillis > 0L)
        require(staleTtlMillis >= freshTtlMillis)
        require(negativeTtlMillis > 0L)
        require(negativeTtlMillis <= staleTtlMillis)
    }

    @Synchronized
    fun getFresh(key: String, nowMillis: Long): List<SManga>? =
        get(key, nowMillis, freshTtlMillis)

    @Synchronized
    fun getStale(key: String, nowMillis: Long): List<SManga>? =
        get(key, nowMillis, staleTtlMillis)

    /** Returns when an unexpired empty response should be probed again. */
    @Synchronized
    fun negativeCacheUntil(key: String, nowMillis: Long): Long? {
        val entry = entries[key] ?: return null
        if (entry.manga.isNotEmpty()) return null
        val age = if (nowMillis < entry.storedAtMillis) 0L else nowMillis - entry.storedAtMillis
        if (age >= negativeTtlMillis) {
            entries.remove(key)
            return null
        }
        return if (entry.storedAtMillis > Long.MAX_VALUE - negativeTtlMillis) {
            Long.MAX_VALUE
        } else {
            entry.storedAtMillis + negativeTtlMillis
        }
    }

    @Synchronized
    fun put(key: String, manga: List<SManga>, nowMillis: Long) {
        if (key.isBlank()) return
        entries[key] = Entry(nowMillis, manga.map(SManga::copy))
        while (entries.size > maxEntries) entries.remove(entries.keys.first())
    }

    private fun get(key: String, nowMillis: Long, ttlMillis: Long): List<SManga>? {
        val entry = entries[key] ?: return null
        val age = if (nowMillis < entry.storedAtMillis) 0L else nowMillis - entry.storedAtMillis
        val retentionTtl = if (entry.manga.isEmpty()) negativeTtlMillis else staleTtlMillis
        if (age >= retentionTtl) {
            entries.remove(key)
            return null
        }
        val requestedTtl = if (entry.manga.isEmpty()) negativeTtlMillis else ttlMillis
        if (age >= requestedTtl) return null
        return entry.manga.map(SManga::copy)
    }

    private data class Entry(
        val storedAtMillis: Long,
        val manga: List<SManga>,
    )

    private companion object {
        const val DEFAULT_MAX_ENTRIES = 100
        const val DEFAULT_FRESH_TTL_MILLIS = 30 * 60 * 1000L
        const val DEFAULT_STALE_TTL_MILLIS = 6 * 60 * 60 * 1000L
        const val DEFAULT_NEGATIVE_TTL_MILLIS = 15 * 60 * 1000L
    }
}
