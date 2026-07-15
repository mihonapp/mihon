package eu.kanade.tachiyomi.data.recommendation

import eu.kanade.tachiyomi.source.model.SManga
import java.text.Normalizer
import java.util.Locale

/** User-defined metadata terms that must never appear in recommendation rows. */
internal object RecommendationKeywordFilter {

    private val separator = Regex("""\s*(?:,|;|\r?\n|、|，|；|\|)\s*""")
    private val asciiWord = Regex("""^[a-z0-9][a-z0-9 _-]*$""")

    fun parse(value: String): List<String> {
        return value.split(separator)
            .map(::normalize)
            .filter(String::isNotBlank)
            .distinct()
    }

    fun matches(manga: SManga, terms: List<String>): Boolean {
        if (terms.isEmpty()) return false
        val metadata = listOfNotNull(
            RecommendationMetadata.safeTitle(manga),
            manga.author,
            manga.artist,
            manga.genre,
            manga.description,
        ).joinToString("\n", transform = ::normalize)
        return terms.any { term -> containsTerm(metadata, term) }
    }

    private fun containsTerm(metadata: String, term: String): Boolean {
        if (!asciiWord.matches(term)) return metadata.contains(term)
        var index = metadata.indexOf(term)
        while (index >= 0) {
            val end = index + term.length
            val hasLeftBoundary = index == 0 || !metadata[index - 1].isLetterOrDigit()
            val hasRightBoundary = end == metadata.length || !metadata[end].isLetterOrDigit()
            if (hasLeftBoundary && hasRightBoundary) return true
            index = metadata.indexOf(term, startIndex = index + 1)
        }
        return false
    }

    private fun normalize(value: String): String {
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .trim()
            .replace(Regex("""\s+"""), " ")
    }
}
