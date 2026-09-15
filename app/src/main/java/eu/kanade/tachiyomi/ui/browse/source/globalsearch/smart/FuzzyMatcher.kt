package eu.kanade.tachiyomi.ui.browse.source.globalsearch.smart

import kotlin.math.max

/**
 * Computes a relevance score between a normalized query and a normalized title,
 * returning a value in [0.0, 1.0] where 1.0 is a perfect match. The scoring is
 * "Google-like" in that it tolerates typos, partial words, and word-order changes
 * instead of requiring exact matches.
 */
object FuzzyMatcher {

    /**
     * Returns a relevance score in [0, 1]. [query] and [title] should both be
     * pre-normalized via [QueryNormalizer.normalize].
     */
    fun score(query: String, title: String): Float {
        if (query.isEmpty() || title.isEmpty()) return 0f

        if (query == title) return 1f

        val qTokens = QueryNormalizer.tokenize(query)
        val tTokens = QueryNormalizer.tokenize(title)

        // Whole-query structural matches
        if (isSubsequence(query, title)) return 0.85f
        if (title.startsWith(query)) return 0.9f

        // Token-level matching
        var sumScores = 0f
        var matchedTokens = 0
        for (qt in qTokens) {
            var best = 0f
            for (tt in tTokens) {
                val s = tokenScore(qt, tt)
                if (s > best) best = s
            }
            if (best > 0f) {
                sumScores += best
                matchedTokens++
            }
        }

        if (qTokens.isEmpty()) return 0f

        val coverage = matchedTokens.toFloat() / qTokens.size
        val avgQuality = if (matchedTokens == 0) 0f else sumScores / matchedTokens

        // Subsequence across the whole run of title tokens (helps concatenated titles)
        val crossSubsequenceBonus = if (isJoinedSubsequence(qTokens, tTokens)) 0.1f else 0f

        return (coverage * avgQuality + crossSubsequenceBonus).coerceIn(0f, 0.95f)
    }

    /**
     * Score a single query token against a single title token. Returns 0 if no match.
     */
    fun tokenScore(queryToken: String, titleToken: String): Float {
        if (queryToken.isEmpty() || titleToken.isEmpty()) return 0f

        if (queryToken == titleToken) return 1f

        // Prefix match (e.g. "solo" vs "sololeveling")
        if (queryToken.length >= 3 && titleToken.startsWith(queryToken)) return 0.85f
        if (titleToken.length >= 3 && queryToken.startsWith(titleToken)) return 0.8f

        // Subsequence within a token
        if (queryToken.length <= titleToken.length && isSubsequence(queryToken, titleToken)) return 0.6f

        // Fuzzy edit-distance for typo tolerance on longer tokens
        if (queryToken.length >= 4 && titleToken.length >= 4) {
            val maxDist = if (queryToken.length >= 8) 2 else 1
            val dist = QueryNormalizer.levenshtein(queryToken, titleToken, maxDist)
            if (dist <= maxDist) {
                val len = max(queryToken.length, titleToken.length)
                val similarity = 1f - (dist.toFloat() / len.toFloat())
                return (0.4f + similarity * 0.5f).coerceIn(0f, 0.85f)
            }
        }

        return 0f
    }

    /**
     * True if every query token is a subsequence appearing, in order, somewhere within
     * the entire title (spans across word boundaries). E.g. query "opm" tokens and title
     * "one punch man".
     */
    private fun isJoinedSubsequence(queryTokens: List<String>, titleTokens: List<String>): Boolean {
        // Concatenate title tokens to allow cross-word subsequence matching
        val joinedTitle = titleTokens.joinToString("")
        val joinedQuery = queryTokens.joinToString("")
        val match = isSubsequence(joinedQuery, joinedTitle)
        return match && joinedQuery.length >= 3
    }

    /**
     * True if [a] is a subsequence of [b] (chars of [a] appear in order within [b]).
     */
    fun isSubsequence(a: String, b: String): Boolean {
        if (a.isEmpty()) return true
        var j = 0
        for (i in b.indices) {
            if (b[i] == a[j]) {
                j++
                if (j == a.length) return true
            }
        }
        return false
    }
}
