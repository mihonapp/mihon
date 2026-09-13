package eu.kanade.tachiyomi.ui.browse.source.globalsearch.smart

import tachiyomi.domain.manga.model.Manga

/**
 * Aggregates and ranks manga search results across multiple sources into a single
 * smart, deduplicated list ordered by relevance to the normalized query.
 */
object SmartSearchEngine {

    /**
     * A single merged search result. Multiple [Manga] (one per source) that refer to the
     * same series (same normalized title) are grouped together under one entry.
     *
     * @property score      relevance score in [0, 1]
     * @property primary    the manga used for display (title/cover)
     * @property alternatives other sources/URLs that also matched this series
     */
    data class MergedResult(
        val score: Float,
        val primary: Manga,
        val alternatives: List<Manga> = emptyList(),
    ) {
        val title: String get() = primary.title
        val sourceId: Long get() = primary.source
    }

    const val MIN_SCORE = 0.20f

    /**
     * Merges and ranks [rawResults] against [normalizedQuery].
     *
     * Dedup strategy: titles with the same normalized form are merged into one
     * [MergedResult]. The highest-scoring manga (best title match) becomes primary;
     * the rest are kept as [alternatives] so the user can still pick another source.
     */
    fun merge(
        normalizedQuery: String,
        rawResults: List<Manga>,
    ): List<MergedResult> {
        if (normalizedQuery.isEmpty() || rawResults.isEmpty()) return emptyList()

        // Group by normalized title -> keep best-scoring Manga per group
        val grouped = LinkedHashMap<String, MutableList<Pair<Float, Manga>>>()

        for (manga in rawResults) {
            val normTitle = QueryNormalizer.normalize(manga.title)
            if (normTitle.isEmpty()) continue
            val s = FuzzyMatcher.score(normalizedQuery, normTitle)
            if (s < MIN_SCORE) continue
            grouped.getOrPut(normTitle) { mutableListOf() }.add(s to manga)
        }

        return grouped.values
            .map { group ->
                val sorted = group.sortedByDescending { it.first }
                val best = sorted.first()
                val bestManga = best.second
                val alternatives = sorted.drop(1)
                    .map { it.second }
                    .filter { it.source != bestManga.source || it.url != bestManga.url }
                MergedResult(
                    score = best.first,
                    primary = bestManga,
                    alternatives = alternatives,
                )
            }
            .sortedWith(
                compareByDescending<MergedResult> { it.score }
                    .thenBy { it.title.lowercase() },
            )
    }

    /**
     * Generates "did you mean" style suggestions when a search yields no results.
     *
     * Works self-contained without requiring external title lists: for each query token we
     * look for a known close variant (from [QueryNormalizer.aliasMap] plus a small set of
     * common manga-term corrections) within edit distance 2 and propose the full corrected
     * query. This handles the common case (e.g. "levling" -> "leveling") even when sources
     * returned nothing.
     *
     * @param dictionary additional known terms to consider (e.g. series titles already seen).
     */
    fun suggest(
        normalizedQuery: String,
        dictionary: List<String> = emptyList(),
        maxSuggestions: Int = 3,
    ): List<String> {
        if (normalizedQuery.isBlank()) return emptyList()

        val tokens = QueryNormalizer.tokenize(normalizedQuery)
        if (tokens.isEmpty()) return emptyList()

        val candidates = buildSet {
            addAll(QueryNormalizer.aliasMap.keys)
            addAll(QueryNormalizer.aliasMap.values.flatMap { QueryNormalizer.tokenize(it) })
            addAll(dictionary)
            addAll(COMMON_TERMS)
        }

        val suggestions = LinkedHashSet<String>()

        for ((index, token) in tokens.withIndex()) {
            if (token.length < 4) continue
            var bestWord: String? = null
            var bestDist = Int.MAX_VALUE
            for (word in candidates) {
                val nw = QueryNormalizer.normalize(word)
                if (nw.length < 4 || nw == token) continue
                // Allow matching against each word token of the candidate too
                val dist = QueryNormalizer.tokenize(nw)
                    .minOfOrNull { QueryNormalizer.levenshtein(token, it, 2) } ?: Int.MAX_VALUE
                if (dist in 1..2 && dist < bestDist) {
                    bestDist = dist
                    bestWord = nw
                }
            }
            if (bestWord != null) {
                val corrected = tokens.toMutableList().also { it[index] = bestWord }
                suggestions.add(corrected.joinToString(" "))
            }
        }

        // Whole-query correction against multi-word candidates
        for (word in candidates) {
            val nw = QueryNormalizer.normalize(word)
            val d = QueryNormalizer.levenshtein(normalizedQuery, nw, 2)
            if (d in 1..2) {
                suggestions.add(nw)
            }
        }

        return suggestions.take(maxSuggestions)
    }

    /** A small set of frequently-misspelled manga term fragments used for suggestions. */
    private val COMMON_TERMS = listOf(
        "leveling", "level", "punch", "piece", "god", "tower", "kingdom", "rank",
        "solo", "hero", "academy", "return", "mount", "hunter", "murim", "noblesse",
        "begin", "berserk", "naruto", "one", "shippuden", "boruto", "reaper", "scans",
        "chapter", "manhwa", "manhua", "manwha", "isekai", "reincarnation",
    )
}
