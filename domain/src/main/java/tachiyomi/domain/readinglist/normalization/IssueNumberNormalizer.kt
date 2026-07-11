package tachiyomi.domain.readinglist.normalization

import java.math.BigDecimal
import java.math.BigInteger
import java.text.Normalizer
import java.util.Locale

data class NormalizedIssueNumber(
    val canonical: String,
    val numericValue: BigDecimal?,
    val suffix: String?,
    val kind: IssueNumberKind,
) {
    val isNumeric: Boolean
        get() = numericValue != null

    val isUsable: Boolean
        get() = canonical.isNotEmpty()

    fun isEquivalentTo(other: NormalizedIssueNumber): Boolean {
        if (!isUsable || !other.isUsable || kind != other.kind) return false

        return if (numericValue != null && other.numericValue != null) {
            numericValue.compareTo(other.numericValue) == 0 && suffix == other.suffix
        } else {
            canonical == other.canonical
        }
    }
}

enum class IssueNumberKind(val canonicalPrefix: String) {
    REGULAR(""),
    ANNUAL("annual"),
    SPECIAL("special"),
    FREE_COMIC_BOOK_DAY("fcbd"),
    ONE_SHOT("one shot"),
    OPAQUE(""),
}

object IssueNumberNormalizer {

    fun normalize(value: String): NormalizedIssueNumber {
        var working = Normalizer.normalize(
            value.trim().expandUnicodeFractions(),
            Normalizer.Form.NFKC,
        )
            .lowercase(Locale.ROOT)
            .normalizeDashes()
            .replace(WHITESPACE, " ")
            .trim()

        if (working.isEmpty()) {
            return NormalizedIssueNumber(
                canonical = "",
                numericValue = null,
                suffix = null,
                kind = IssueNumberKind.OPAQUE,
            )
        }

        val kindResult = working.extractIssueKind()
        working = kindResult.remaining
            .removeIssueLabel()
            .removeTrailingIssueCount()
            .trim()

        parseAsciiFraction(working)?.let { number ->
            return numericResult(kindResult.kind, number)
        }

        val numericMatch = NUMERIC_WITH_OPTIONAL_SUFFIX.matchEntire(working)
        if (numericMatch != null) {
            val number = numericMatch.groupValues[1]
                .replace(',', '.')
                .toBigDecimalOrNull()
            if (number != null) {
                val suffix = numericMatch.groupValues[2]
                    .takeIf(String::isNotBlank)
                    ?.lowercase(Locale.ROOT)
                return numericResult(kindResult.kind, number, suffix)
            }
        }

        val opaque = working
            .replace(WHITESPACE_AROUND_DASH, "-")
            .replace(WHITESPACE, " ")
            .trim(' ', '.', '#')
        val canonical = listOf(kindResult.kind.canonicalPrefix, opaque)
            .filter(String::isNotEmpty)
            .joinToString(" ")

        return NormalizedIssueNumber(
            canonical = canonical,
            numericValue = null,
            suffix = null,
            kind = if (kindResult.kind == IssueNumberKind.REGULAR) IssueNumberKind.OPAQUE else kindResult.kind,
        )
    }

    private fun numericResult(
        kind: IssueNumberKind,
        number: BigDecimal,
        suffix: String? = null,
    ): NormalizedIssueNumber {
        val normalizedNumber = number.normalized()
        val numberText = normalizedNumber.toPlainString()
        val canonicalNumber = numberText + suffix.orEmpty()
        val canonical = listOf(kind.canonicalPrefix, canonicalNumber)
            .filter(String::isNotEmpty)
            .joinToString(" ")

        return NormalizedIssueNumber(
            canonical = canonical,
            numericValue = normalizedNumber,
            suffix = suffix,
            kind = kind,
        )
    }

    private fun parseAsciiFraction(value: String): BigDecimal? {
        MIXED_ASCII_FRACTION.matchEntire(value)?.let { match ->
            val whole = match.groupValues[1].toBigDecimalOrNull() ?: return null
            val fraction = finiteFraction(
                numerator = match.groupValues[2].toBigIntegerOrNull() ?: return null,
                denominator = match.groupValues[3].toBigIntegerOrNull() ?: return null,
            ) ?: return null
            return if (whole.signum() < 0) whole - fraction else whole + fraction
        }

        val match = ASCII_FRACTION.matchEntire(value) ?: return null
        return finiteFraction(
            numerator = match.groupValues[1].toBigIntegerOrNull() ?: return null,
            denominator = match.groupValues[2].toBigIntegerOrNull() ?: return null,
        )
    }

    private fun finiteFraction(numerator: BigInteger, denominator: BigInteger): BigDecimal? {
        if (denominator == BigInteger.ZERO) return null

        var reducedDenominator = denominator.abs()
        while (reducedDenominator.mod(TWO) == BigInteger.ZERO) {
            reducedDenominator /= TWO
        }
        while (reducedDenominator.mod(FIVE) == BigInteger.ZERO) {
            reducedDenominator /= FIVE
        }
        if (reducedDenominator != BigInteger.ONE) return null

        return BigDecimal(numerator).divide(BigDecimal(denominator))
    }

    private data class KindResult(
        val kind: IssueNumberKind,
        val remaining: String,
    )

    private fun String.extractIssueKind(): KindResult {
        val prefix = ISSUE_KIND_PREFIXES.firstNotNullOfOrNull { (pattern, kind) ->
            pattern.find(this)?.let { match ->
                KindResult(
                    kind = kind,
                    remaining = removeRange(match.range).trim(),
                )
            }
        }
        if (prefix != null) return prefix

        val suffix = ISSUE_KIND_SUFFIXES.firstNotNullOfOrNull { (pattern, kind) ->
            pattern.find(this)?.let { match ->
                KindResult(
                    kind = kind,
                    remaining = removeRange(match.range).trim(),
                )
            }
        }
        return suffix ?: KindResult(IssueNumberKind.REGULAR, this)
    }
}

private fun String.expandUnicodeFractions(): String {
    val output = StringBuilder(length)
    forEach { character ->
        val fraction = UNICODE_FRACTION_TEXT[character]
        if (fraction == null) {
            output.append(character)
        } else {
            if (output.isNotEmpty() && output.last().isDigit()) {
                output.append(' ')
            }
            output.append(fraction)
        }
    }
    return output.toString()
}

private fun String.removeIssueLabel(): String {
    var result = this
    while (true) {
        val updated = LEADING_ISSUE_LABEL.replaceFirst(result, "").trimStart()
        if (updated == result) return result
        result = updated
    }
}

private fun String.removeTrailingIssueCount(): String {
    return TRAILING_ISSUE_COUNT.replace(this, "").trimEnd()
}

private fun String.normalizeDashes(): String {
    return replace('–', '-').replace('—', '-').replace('−', '-')
}

private fun BigDecimal.normalized(): BigDecimal {
    val stripped = stripTrailingZeros()
    return if (stripped.scale() < 0) stripped.setScale(0) else stripped
}

private val ISSUE_KIND_PREFIXES = listOf(
    Regex("""^(?:free\s+comic\s+book\s+day|fcbd)\b\s*[:#-]?\s*""") to IssueNumberKind.FREE_COMIC_BOOK_DAY,
    Regex("""^(?:one[ -]?shot|oneshot)\b\s*[:#-]?\s*""") to IssueNumberKind.ONE_SHOT,
    Regex("""^annual\b\s*[:#-]?\s*""") to IssueNumberKind.ANNUAL,
    Regex("""^special\b\s*[:#-]?\s*""") to IssueNumberKind.SPECIAL,
)

private val ISSUE_KIND_SUFFIXES = listOf(
    Regex("""\s*[-:]?\s*(?:free\s+comic\s+book\s+day|fcbd)$""") to IssueNumberKind.FREE_COMIC_BOOK_DAY,
    Regex("""\s*[-:]?\s*(?:one[ -]?shot|oneshot)$""") to IssueNumberKind.ONE_SHOT,
    Regex("""\s*[-:]?\s*annual$""") to IssueNumberKind.ANNUAL,
    Regex("""\s*[-:]?\s*special$""") to IssueNumberKind.SPECIAL,
)

private val LEADING_ISSUE_LABEL = Regex(
    """^(?:(?:issue|iss|chapter|ch|number|no)\.?\s*|#+\s*)""",
)
private val TRAILING_ISSUE_COUNT = Regex(
    """\s*(?:\(\s*of\s+\d+\s*\)|\s+of\s+\d+)\s*$""",
)
private val NUMERIC_WITH_OPTIONAL_SUFFIX = Regex(
    """^([+-]?\d+(?:[\.,]\d+)?)(?:\s*[-._]?\s*([a-z][a-z0-9]*))?$""",
)
private val ASCII_FRACTION = Regex("""^([+-]?\d+)\s*/\s*(\d+)$""")
private val MIXED_ASCII_FRACTION = Regex("""^([+-]?\d+)\s+(\d+)\s*/\s*(\d+)$""")
private val WHITESPACE = Regex("""\s+""")
private val WHITESPACE_AROUND_DASH = Regex("""\s*-\s*""")
private val TWO = BigInteger.valueOf(2)
private val FIVE = BigInteger.valueOf(5)
private val UNICODE_FRACTION_TEXT = mapOf(
    '½' to "1/2",
    '⅓' to "1/3",
    '⅔' to "2/3",
    '¼' to "1/4",
    '¾' to "3/4",
    '⅛' to "1/8",
    '⅜' to "3/8",
    '⅝' to "5/8",
    '⅞' to "7/8",
)
