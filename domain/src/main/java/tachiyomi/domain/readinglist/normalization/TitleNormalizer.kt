package tachiyomi.domain.readinglist.normalization

import java.text.Normalizer
import java.util.Locale

data class NormalizedTitle(
    val canonical: String,
    val base: String,
    val articlelessBase: String,
    val tokens: List<String>,
    val year: Int?,
    val volume: Int?,
) {
    val comparisonKeys: Set<String>
        get() = linkedSetOf(
            canonical,
            canonical.removeLeadingArticle(),
            base,
            articlelessBase,
        ).filterTo(linkedSetOf()) { it.isNotEmpty() }

    val isUsable: Boolean
        get() = base.isNotEmpty()

    fun isEquivalentTo(other: NormalizedTitle): Boolean {
        return isUsable && other.isUsable && comparisonKeys.any(other.comparisonKeys::contains)
    }
}

object TitleNormalizer {

    fun normalize(value: String): NormalizedTitle {
        val compatibilityNormalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFKC)
        val canonical = compatibilityNormalized.toComparisonText()
        val edition = stripTrailingEditionMetadata(compatibilityNormalized)
        val base = edition.title.toComparisonText()

        return NormalizedTitle(
            canonical = canonical,
            base = base,
            articlelessBase = base.removeLeadingArticle(),
            tokens = base.splitToTokens(),
            year = edition.year,
            volume = edition.volume,
        )
    }

    private fun stripTrailingEditionMetadata(value: String): EditionMetadata {
        var title = value.trim()
        var year: Int? = null
        var volume: Int? = null

        repeat(MAX_EDITION_SUFFIXES) {
            val yearMatch = TRAILING_YEAR.find(title)
            if (year == null && yearMatch != null) {
                year = yearMatch.groupValues[1].toInt()
                title = title.removeRange(yearMatch.range).trimEnd()
                return@repeat
            }

            val volumeMatch = TRAILING_VOLUME.find(title)
            if (volume == null && volumeMatch != null) {
                volume = volumeMatch.groupValues[1].toVolumeNumber()
                if (volume != null) {
                    title = title.removeRange(volumeMatch.range).trimEnd()
                    return@repeat
                }
            }

            return EditionMetadata(
                title = title,
                year = year,
                volume = volume,
            )
        }

        return EditionMetadata(
            title = title,
            year = year,
            volume = volume,
        )
    }

    private data class EditionMetadata(
        val title: String,
        val year: Int?,
        val volume: Int?,
    )

    private const val MAX_EDITION_SUFFIXES = 3

    private val TRAILING_YEAR = Regex(
        pattern = """\s*[\(\[]\s*((?:18|19|20|21)\d{2})\s*[\)\]]\s*$""",
    )

    private val TRAILING_VOLUME = Regex(
        pattern = """(?i)\s*(?:[-–—,:]\s*)?[\(\[]?\s*(?:vol(?:ume)?\.?|v)\s*([0-9]+|[ivxlcdm]+)\s*[\)\]]?\s*$""",
    )
}

internal fun String.toComparisonText(): String {
    if (isBlank()) return ""

    val decomposed = Normalizer.normalize(this, Normalizer.Form.NFKD)
    val output = StringBuilder(decomposed.length)

    decomposed.forEach { character ->
        when {
            character.isCombiningMark() -> Unit
            character == '&' -> output.append(" and ")
            character == '\'' || character == '’' || character == 'ʼ' -> Unit
            character == 'ß' -> output.append("ss")
            character == 'æ' || character == 'Æ' -> output.append("ae")
            character == 'œ' || character == 'Œ' -> output.append("oe")
            character == 'ø' || character == 'Ø' -> output.append('o')
            character == 'ł' || character == 'Ł' -> output.append('l')
            character.isLetterOrDigit() -> output.append(character.lowercaseChar())
            else -> output.append(' ')
        }
    }

    return output
        .toString()
        .lowercase(Locale.ROOT)
        .replace(WHITESPACE, " ")
        .trim()
}

private fun Char.isCombiningMark(): Boolean {
    return when (Character.getType(this)) {
        Character.NON_SPACING_MARK.toInt(),
        Character.COMBINING_SPACING_MARK.toInt(),
        Character.ENCLOSING_MARK.toInt(),
        -> true
        else -> false
    }
}

private fun String.removeLeadingArticle(): String {
    return LEADING_ARTICLE.replace(this, "")
}

private fun String.splitToTokens(): List<String> {
    return if (isEmpty()) emptyList() else split(' ')
}

private fun String.toVolumeNumber(): Int? {
    toIntOrNull()?.let { return it.takeIf { number -> number > 0 } }

    val roman = lowercase(Locale.ROOT)
    if (!ROMAN_NUMERAL.matches(roman)) return null

    var result = 0
    var previous = 0
    for (index in roman.indices.reversed()) {
        val current = ROMAN_VALUES.getValue(roman[index])
        if (current < previous) {
            result -= current
        } else {
            result += current
            previous = current
        }
    }
    return result.takeIf { it > 0 }
}

private val WHITESPACE = Regex("""\s+""")
private val LEADING_ARTICLE = Regex("""^(?:the|a|an)\s+""")
private val ROMAN_NUMERAL = Regex("""[ivxlcdm]+""")
private val ROMAN_VALUES = mapOf(
    'i' to 1,
    'v' to 5,
    'x' to 10,
    'l' to 50,
    'c' to 100,
    'd' to 500,
    'm' to 1000,
)
