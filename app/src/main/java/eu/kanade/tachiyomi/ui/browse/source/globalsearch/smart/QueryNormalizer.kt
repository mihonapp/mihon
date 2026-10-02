package eu.kanade.tachiyomi.ui.browse.source.globalsearch.smart

import java.util.Locale
import kotlin.math.min

/**
 * Normalizes a user search query into a canonical form that is more likely to
 * produce results on exact-match source backends, and (later) used for fuzzy
 * ranking of returned titles.
 *
 * Improvements over the plain raw query:
 *  - lower-cases everything
 *  - strips accents/diacritics so "shonen" matches "shōnen"
 *  - removes punctuation and collapses whitespace
 *  - expands common romanization/alias variants
 */
object QueryNormalizer {

    private val accentMap = mapOf(
        'à' to 'a', 'á' to 'a', 'â' to 'a', 'ã' to 'a', 'ä' to 'a', 'å' to 'a', 'ā' to 'a', 'ă' to 'a', 'ą' to 'a',
        'ç' to 'c', 'ć' to 'c', 'č' to 'c',
        'è' to 'e', 'é' to 'e', 'ê' to 'e', 'ë' to 'e', 'ē' to 'e', 'ė' to 'e', 'ę' to 'e', 'ě' to 'e',
        'ì' to 'i', 'í' to 'i', 'î' to 'i', 'ï' to 'i', 'ī' to 'i', 'į' to 'i', 'ı' to 'i',
        'ñ' to 'n', 'ń' to 'n', 'ň' to 'n',
        'ò' to 'o', 'ó' to 'o', 'ô' to 'o', 'õ' to 'o', 'ö' to 'o', 'ō' to 'o', 'ø' to 'o', 'ő' to 'o',
        'ù' to 'u', 'ú' to 'u', 'û' to 'u', 'ü' to 'u', 'ū' to 'u', 'ů' to 'u', 'ű' to 'u',
        'ý' to 'y', 'ÿ' to 'y', 'ž' to 'z', 'ź' to 'z', 'ż' to 'z',
        'ł' to 'l',
    )

    /**
     * Alias variants -> canonical expansion. Applied token-boundary aware, case-insensitive,
     * so different romanizations and common typos map to a consistent form.
     */
    @JvmField
    val aliasMap: Map<String, String> = mapOf(
        // Different romanizations of the same Japanese words
        "shonen" to "shounen",
        "shoujo" to "shoujo",
        "shojo" to "shoujo",
        "manhua" to "manhwa manhua",
        "manwha" to "manhwa", // common typo of manhwa
        // Long-standing typos
        "manga dex" to "mangadex",
        "solo leve rule" to "solo leveling",
    )

    /**
     * Normalizes a raw query into a lower-cased, accent/punctuation-free, single-space
     * separated string. Applies alias expansion. Output is safe for case-insensitive matching.
     */
    @JvmOverloads
    fun normalize(raw: String): String {
        val lower = raw.lowercase(Locale.ROOT)

        // 1. Strip accents
        val stripped = buildString(lower.length) {
            lower.forEach { ch -> append(accentMap[ch] ?: ch) }
        }

        // 2. Keep only letters/numbers/whitespace, collapse whitespace
        val cleaned = stripped
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        // 3. Alias expansion (token-boundary aware, case-insensitive)
        val result = aliasMap.keys.fold(cleaned) { acc, key ->
            acc.replace(Regex("\\b${Regex.escape(key)}\\b", RegexOption.IGNORE_CASE), aliasMap.getValue(key))
        }
            .replace(Regex("\\s+"), " ")
            .trim()

        return result
    }

    /**
     * Splits a normalized query into individual word tokens.
     */
    fun tokenize(normalized: String): List<String> = normalized.split(' ')

    /**
     * Levenshtein distance, capped at [max] for efficiency. Used by the fuzzy matcher
     * and for "did you mean" suggestions.
     */
    fun levenshtein(a: String, b: String, max: Int = Int.MAX_VALUE): Int {
        if (a == b) return 0
        if (a.isEmpty()) return min(b.length, max)
        if (b.isEmpty()) return min(a.length, max)
        // Quick rejection: length difference already exceeds max
        if (kotlin.math.abs(a.length - b.length) > max) return max + 1

        val prev = IntArray(b.length + 1) { it }
        val curr = IntArray(b.length + 1)

        for (i in 1..a.length) {
            curr[0] = i
            var bestInRow = curr[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                val deletion = prev[j] + 1
                val insertion = curr[j - 1] + 1
                val substitution = prev[j - 1] + cost
                curr[j] = minOf(deletion, insertion, substitution)
                if (curr[j] < bestInRow) bestInRow = curr[j]
            }
            if (bestInRow > max) return max + 1
            prev.indices.forEach { prev[it] = curr[it] }
        }
        return curr[b.length]
    }
}
