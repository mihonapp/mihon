package tachiyomi.domain.readinglist.matching

import tachiyomi.domain.readinglist.normalization.IssueNumberKind
import tachiyomi.domain.readinglist.normalization.IssueNumberNormalizer
import tachiyomi.domain.readinglist.normalization.NormalizedIssueNumber
import java.math.BigDecimal

object ReadingListChapterIssueExtractor {

    fun extract(
        seriesTitle: String,
        chapterName: String,
        chapterNumber: Float,
        expectedIssue: String,
    ): String? {
        val cleanedName = chapterName.trim()
        val withoutSeries = cleanedName.removeLeadingSeriesTitle(seriesTitle)
        val explicitRawCandidates = linkedSetOf<String>()
        val weakRawCandidates = linkedSetOf<String>()

        KIND_WITH_FOLLOWING_ISSUE.findAll(withoutSeries).forEach { match ->
            explicitRawCandidates.addIfNotBlank(match.value)
        }
        ISSUE_BEFORE_KIND.findAll(withoutSeries).forEach { match ->
            explicitRawCandidates.addIfNotBlank(match.value)
        }
        KIND_PREFIX.find(withoutSeries)?.let { match ->
            explicitRawCandidates.addIfNotBlank(match.value)
        }
        LABELED_ISSUE.findAll(withoutSeries).forEach { match ->
            explicitRawCandidates.addIfNotBlank(match.groupValues[1])
        }
        HASH_ISSUE.findAll(withoutSeries).forEach { match ->
            explicitRawCandidates.addIfNotBlank(match.groupValues[1])
        }

        LEADING_ISSUE.find(withoutSeries)?.let { match ->
            weakRawCandidates.addIfNotBlank(match.groupValues[1])
        }
        TRAILING_ISSUE.find(withoutSeries)?.let { match ->
            weakRawCandidates.addIfNotBlank(match.groupValues[1])
        }
        weakRawCandidates.addIfNotBlank(withoutSeries)

        val expected = IssueNumberNormalizer.normalize(expectedIssue)
        val explicitCandidates = explicitRawCandidates.mapNotNull { value ->
            value.toParsedCandidate()
        }
        val weakCandidates = weakRawCandidates
            .mapNotNull { value -> value.toParsedCandidate() }
            .filter { candidate ->
                candidate.kind == IssueNumberKind.REGULAR ||
                    candidate.kind == IssueNumberKind.OPAQUE
            }
        val allCandidates = (explicitCandidates + weakCandidates)
            .distinctBy { candidate -> candidate.normalized }

        val distinctionPreservingCandidates = allCandidates.filter { candidate ->
            (
                candidate.kind != IssueNumberKind.REGULAR &&
                    candidate.kind != IssueNumberKind.OPAQUE
                ) ||
                candidate.normalized.suffix != null
        }
        distinctionPreservingCandidates
            .firstOrNull { candidate -> expected.isEquivalentTo(candidate.normalized) }
            ?.let { candidate -> return candidate.raw }
        distinctionPreservingCandidates.firstOrNull()?.let { candidate ->
            return candidate.raw
        }

        val explicitRegularCandidates = explicitCandidates.filter { candidate ->
            candidate.kind == IssueNumberKind.REGULAR
        }
        explicitRegularCandidates
            .firstOrNull { candidate -> expected.isEquivalentTo(candidate.normalized) }
            ?.let { candidate -> return candidate.raw }
        explicitRegularCandidates.firstOrNull()?.let { candidate ->
            return candidate.raw
        }

        val weakRegularCandidates = weakCandidates.filter { candidate ->
            candidate.kind == IssueNumberKind.REGULAR
        }
        weakRegularCandidates
            .firstOrNull { candidate -> expected.isEquivalentTo(candidate.normalized) }
            ?.let { candidate -> return candidate.raw }

        allCandidates
            .filter { candidate -> candidate.kind == IssueNumberKind.OPAQUE }
            .firstOrNull { candidate -> expected.isEquivalentTo(candidate.normalized) }
            ?.let { candidate -> return candidate.raw }

        val sourceNumberCandidate = chapterNumber.toParsedCandidate()
        if (
            sourceNumberCandidate != null &&
            expected.isEquivalentTo(sourceNumberCandidate.normalized)
        ) {
            return sourceNumberCandidate.raw
        }

        weakRegularCandidates.firstOrNull()?.let { candidate ->
            return candidate.raw
        }
        sourceNumberCandidate?.let { candidate ->
            return candidate.raw
        }
        return allCandidates
            .firstOrNull { candidate -> candidate.kind == IssueNumberKind.OPAQUE }
            ?.raw
    }

    private fun String.toParsedCandidate(): ParsedCandidate? {
        val normalized = IssueNumberNormalizer.normalize(this)
        return takeIf { normalized.isUsable }?.let { raw ->
            ParsedCandidate(
                raw = raw,
                kind = normalized.kind,
                normalized = normalized,
            )
        }
    }

    private fun Float.toParsedCandidate(): ParsedCandidate? {
        if (!isFinite() || this == UNKNOWN_CHAPTER_NUMBER) return null
        return toStableNumberText().toParsedCandidate()
    }

    private data class ParsedCandidate(
        val raw: String,
        val kind: IssueNumberKind,
        val normalized: NormalizedIssueNumber,
    )
}

private fun MutableSet<String>.addIfNotBlank(value: String) {
    value.trim()
        .trim(' ', '-', '–', '—', ':')
        .takeIf(String::isNotBlank)
        ?.let { normalizedValue -> add(normalizedValue) }
}

private fun String.removeLeadingSeriesTitle(seriesTitle: String): String {
    val title = seriesTitle.trim()
    if (title.isEmpty()) return this

    return if (startsWith(title, ignoreCase = true)) {
        removePrefixIgnoreCase(title)
            .trimStart(' ', '-', '–', '—', ':')
    } else {
        this
    }
}

private fun String.removePrefixIgnoreCase(prefix: String): String {
    return if (startsWith(prefix, ignoreCase = true)) substring(prefix.length) else this
}

private fun Float.toStableNumberText(): String {
    return BigDecimal(toString()).stripTrailingZeros().toPlainString()
}

private const val UNKNOWN_CHAPTER_NUMBER = -1.0f
private const val ISSUE_NUMBER_PATTERN =
    """[+-]?(?:\d+\s+\d+\s*/\s*\d+|\d+\s*/\s*\d+|\d+(?:[\.,]\d+)?)(?:[-._]?[a-z][a-z0-9]*)?"""
private const val ISSUE_KIND_PATTERN =
    """(?:free\s+comic\s+book\s+day|fcbd|one[ -]?shot|oneshot|annual|special)"""

private val KIND_WITH_FOLLOWING_ISSUE = Regex(
    pattern =
    """(?i)\b$ISSUE_KIND_PATTERN(?!\s+edition\b)\s*[:#-]?\s*$ISSUE_NUMBER_PATTERN(?=$|[\s:\-–—])""",
)
private val ISSUE_BEFORE_KIND = Regex(
    pattern =
    """(?i)\b$ISSUE_NUMBER_PATTERN\s*[:\-–—]?\s*$ISSUE_KIND_PATTERN(?!\s+edition\b)(?=$|[\s:\-–—])""",
)
private val KIND_PREFIX = Regex(
    pattern = """(?i)^$ISSUE_KIND_PATTERN(?!\s+edition\b)(?=$|\s*[:\-–—])""",
)
private val LABELED_ISSUE = Regex(
    pattern = """(?i)\b(?:issue|iss|chapter|ch|number|no)\.?\s*#?\s*($ISSUE_NUMBER_PATTERN)""",
)
private val HASH_ISSUE = Regex(
    pattern = """(?i)#\s*($ISSUE_NUMBER_PATTERN)""",
)
private val LEADING_ISSUE = Regex(
    pattern = """(?i)^\s*($ISSUE_NUMBER_PATTERN)(?:\s|[:\-–—])""",
)
private val TRAILING_ISSUE = Regex(
    pattern = """(?i)(?:^|[\s:\-–—])($ISSUE_NUMBER_PATTERN)\s*$""",
)
