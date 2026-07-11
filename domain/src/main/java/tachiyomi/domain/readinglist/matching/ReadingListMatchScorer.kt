package tachiyomi.domain.readinglist.matching

import tachiyomi.domain.readinglist.model.ReadingListEntryResolutionState
import tachiyomi.domain.readinglist.normalization.IssueNumberNormalizer
import tachiyomi.domain.readinglist.normalization.NormalizedIssueNumber
import tachiyomi.domain.readinglist.normalization.NormalizedTitle
import tachiyomi.domain.readinglist.normalization.TitleNormalizer
import kotlin.math.max
import kotlin.math.round

data class ReadingListMatchQuery(
    val seriesTitle: String,
    val issueNumber: String,
    val volume: Int? = null,
    val year: Int? = null,
)

data class ReadingListMatchCandidate(
    val id: String,
    val sourceId: Long,
    val seriesTitle: String,
    val issueNumber: String,
    val volume: Int? = null,
    val year: Int? = null,
    val sourcePreference: SourcePreferenceLevel = SourcePreferenceLevel.NONE,
    val externalIdentifierEvidence: EvidenceAgreement = EvidenceAgreement.UNKNOWN,
    val confirmedHistory: ConfirmedHistoryEvidence = ConfirmedHistoryEvidence.NONE,
    val userConfirmed: Boolean = false,
)

enum class SourcePreferenceLevel {
    NONE,
    GLOBAL,
    READING_LIST,
    SERIES,
    ENTRY,
}

enum class ConfirmedHistoryEvidence {
    NONE,
    SOURCE,
    SERIES,
}

enum class EvidenceAgreement {
    UNKNOWN,
    MATCH,
    MISMATCH,
}

data class MatchScoringConfig(
    val autoAcceptThreshold: Double = 88.0,
    val reviewThreshold: Double = 65.0,
    val requiredLead: Double = 10.0,
    val minimumTitleSimilarityForAutoMatch: Double = 0.85,
    val titleWeight: Double = 58.0,
    val issueWeight: Double = 30.0,
    val metadataMatchPoints: Double = 4.0,
    val metadataMismatchPoints: Double = -6.0,
    val externalIdentifierMatchPoints: Double = 4.0,
    val externalIdentifierMismatchPoints: Double = -8.0,
    val globalSourcePreferencePoints: Double = 0.5,
    val readingListSourcePreferencePoints: Double = 1.0,
    val seriesSourcePreferencePoints: Double = 2.0,
    val entrySourcePreferencePoints: Double = 3.0,
    val sourceHistoryPoints: Double = 1.0,
    val seriesHistoryPoints: Double = 3.0,
) {
    init {
        require(reviewThreshold in 0.0..100.0)
        require(autoAcceptThreshold in reviewThreshold..100.0)
        require(requiredLead in 0.0..100.0)
        require(minimumTitleSimilarityForAutoMatch in 0.0..1.0)
        require(titleWeight >= 0.0)
        require(issueWeight >= 0.0)
        require(metadataMatchPoints >= 0.0)
        require(metadataMismatchPoints <= 0.0)
        require(externalIdentifierMatchPoints >= 0.0)
        require(externalIdentifierMismatchPoints <= 0.0)
    }
}

data class MatchScoreBreakdown(
    val titleSimilarity: Double,
    val titlePoints: Double,
    val issueEquivalent: Boolean,
    val issuePoints: Double,
    val yearEvidence: EvidenceAgreement,
    val yearPoints: Double,
    val volumeEvidence: EvidenceAgreement,
    val volumePoints: Double,
    val externalIdentifierEvidence: EvidenceAgreement,
    val externalIdentifierPoints: Double,
    val sourcePreference: SourcePreferenceLevel,
    val sourcePreferencePoints: Double,
    val confirmedHistory: ConfirmedHistoryEvidence,
    val confirmedHistoryPoints: Double,
    val total: Double,
)

data class ScoredReadingListMatchCandidate(
    val candidate: ReadingListMatchCandidate,
    val breakdown: MatchScoreBreakdown,
) {
    val score: Double
        get() = breakdown.total
}

enum class MatchDecisionReason {
    NO_CANDIDATES,
    USER_CONFIRMED,
    BELOW_REVIEW_THRESHOLD,
    BELOW_AUTO_THRESHOLD,
    ISSUE_MISMATCH,
    TITLE_TOO_WEAK,
    INSUFFICIENT_LEAD,
    AUTO_ACCEPTED,
}

data class ReadingListMatchDecision(
    val state: ReadingListEntryResolutionState,
    val reason: MatchDecisionReason,
    val rankedCandidates: List<ScoredReadingListMatchCandidate>,
    val leadOverRunnerUp: Double?,
) {
    val leadingCandidate: ScoredReadingListMatchCandidate?
        get() = rankedCandidates.firstOrNull()

    val acceptedCandidate: ScoredReadingListMatchCandidate?
        get() = when (state) {
            ReadingListEntryResolutionState.AUTO_MATCHED,
            ReadingListEntryResolutionState.USER_CONFIRMED,
            -> leadingCandidate
            else -> null
        }
}

class ReadingListMatchScorer(
    private val config: MatchScoringConfig = MatchScoringConfig(),
) {

    fun score(
        query: ReadingListMatchQuery,
        candidate: ReadingListMatchCandidate,
    ): ScoredReadingListMatchCandidate {
        return score(
            query = NormalizedMatchQuery.from(query),
            candidate = candidate,
        )
    }

    fun decide(
        query: ReadingListMatchQuery,
        candidates: List<ReadingListMatchCandidate>,
    ): ReadingListMatchDecision {
        require(candidates.count(ReadingListMatchCandidate::userConfirmed) <= 1) {
            "Only one candidate may be user-confirmed for a reading-list entry"
        }

        val normalizedQuery = NormalizedMatchQuery.from(query)
        val ranked = candidates
            .map { candidate -> score(normalizedQuery, candidate) }
            .sortedWith(CANDIDATE_ORDER)

        if (ranked.isEmpty()) {
            return ReadingListMatchDecision(
                state = ReadingListEntryResolutionState.UNRESOLVED,
                reason = MatchDecisionReason.NO_CANDIDATES,
                rankedCandidates = emptyList(),
                leadOverRunnerUp = null,
            )
        }

        val leading = ranked.first()
        val lead = ranked.getOrNull(1)
            ?.let { runnerUp -> (leading.score - runnerUp.score).roundForDisplay() }

        if (leading.candidate.userConfirmed) {
            return ReadingListMatchDecision(
                state = ReadingListEntryResolutionState.USER_CONFIRMED,
                reason = MatchDecisionReason.USER_CONFIRMED,
                rankedCandidates = ranked,
                leadOverRunnerUp = lead,
            )
        }

        if (leading.score < config.reviewThreshold) {
            return ReadingListMatchDecision(
                state = ReadingListEntryResolutionState.UNRESOLVED,
                reason = MatchDecisionReason.BELOW_REVIEW_THRESHOLD,
                rankedCandidates = ranked,
                leadOverRunnerUp = lead,
            )
        }

        if (leading.score < config.autoAcceptThreshold) {
            return ReadingListMatchDecision(
                state = ReadingListEntryResolutionState.AMBIGUOUS,
                reason = MatchDecisionReason.BELOW_AUTO_THRESHOLD,
                rankedCandidates = ranked,
                leadOverRunnerUp = lead,
            )
        }

        if (!leading.breakdown.issueEquivalent) {
            return ReadingListMatchDecision(
                state = ReadingListEntryResolutionState.AMBIGUOUS,
                reason = MatchDecisionReason.ISSUE_MISMATCH,
                rankedCandidates = ranked,
                leadOverRunnerUp = lead,
            )
        }

        if (leading.breakdown.titleSimilarity < config.minimumTitleSimilarityForAutoMatch) {
            return ReadingListMatchDecision(
                state = ReadingListEntryResolutionState.AMBIGUOUS,
                reason = MatchDecisionReason.TITLE_TOO_WEAK,
                rankedCandidates = ranked,
                leadOverRunnerUp = lead,
            )
        }

        if (lead != null && lead < config.requiredLead) {
            return ReadingListMatchDecision(
                state = ReadingListEntryResolutionState.AMBIGUOUS,
                reason = MatchDecisionReason.INSUFFICIENT_LEAD,
                rankedCandidates = ranked,
                leadOverRunnerUp = lead,
            )
        }

        return ReadingListMatchDecision(
            state = ReadingListEntryResolutionState.AUTO_MATCHED,
            reason = MatchDecisionReason.AUTO_ACCEPTED,
            rankedCandidates = ranked,
            leadOverRunnerUp = lead,
        )
    }

    private fun score(
        query: NormalizedMatchQuery,
        candidate: ReadingListMatchCandidate,
    ): ScoredReadingListMatchCandidate {
        val candidateTitle = TitleNormalizer.normalize(candidate.seriesTitle)
        val candidateIssue = IssueNumberNormalizer.normalize(candidate.issueNumber)
        val titleSimilarity = titleSimilarity(query.title, candidateTitle).roundForDisplay()
        val titlePoints = (titleSimilarity * config.titleWeight).roundForDisplay()
        val issueEquivalent = query.issue.isEquivalentTo(candidateIssue)
        val issuePoints = if (issueEquivalent) config.issueWeight else 0.0
        val yearEvidence = evidence(query.year, candidate.year)
        val yearPoints = metadataPoints(yearEvidence)
        val volumeEvidence = evidence(query.volume, candidate.volume)
        val volumePoints = metadataPoints(volumeEvidence)
        val externalIdentifierPoints = externalIdentifierPoints(candidate.externalIdentifierEvidence)
        val sourcePreferencePoints = sourcePreferencePoints(candidate.sourcePreference)
        val confirmedHistoryPoints = confirmedHistoryPoints(candidate.confirmedHistory)
        val total = (
            titlePoints +
                issuePoints +
                yearPoints +
                volumePoints +
                externalIdentifierPoints +
                sourcePreferencePoints +
                confirmedHistoryPoints
            )
            .coerceIn(0.0, 100.0)
            .roundForDisplay()

        return ScoredReadingListMatchCandidate(
            candidate = candidate,
            breakdown = MatchScoreBreakdown(
                titleSimilarity = titleSimilarity,
                titlePoints = titlePoints,
                issueEquivalent = issueEquivalent,
                issuePoints = issuePoints,
                yearEvidence = yearEvidence,
                yearPoints = yearPoints,
                volumeEvidence = volumeEvidence,
                volumePoints = volumePoints,
                externalIdentifierEvidence = candidate.externalIdentifierEvidence,
                externalIdentifierPoints = externalIdentifierPoints,
                sourcePreference = candidate.sourcePreference,
                sourcePreferencePoints = sourcePreferencePoints,
                confirmedHistory = candidate.confirmedHistory,
                confirmedHistoryPoints = confirmedHistoryPoints,
                total = total,
            ),
        )
    }

    private fun metadataPoints(evidence: EvidenceAgreement): Double {
        return when (evidence) {
            EvidenceAgreement.UNKNOWN -> 0.0
            EvidenceAgreement.MATCH -> config.metadataMatchPoints
            EvidenceAgreement.MISMATCH -> config.metadataMismatchPoints
        }
    }

    private fun externalIdentifierPoints(evidence: EvidenceAgreement): Double {
        return when (evidence) {
            EvidenceAgreement.UNKNOWN -> 0.0
            EvidenceAgreement.MATCH -> config.externalIdentifierMatchPoints
            EvidenceAgreement.MISMATCH -> config.externalIdentifierMismatchPoints
        }
    }

    private fun sourcePreferencePoints(level: SourcePreferenceLevel): Double {
        return when (level) {
            SourcePreferenceLevel.NONE -> 0.0
            SourcePreferenceLevel.GLOBAL -> config.globalSourcePreferencePoints
            SourcePreferenceLevel.READING_LIST -> config.readingListSourcePreferencePoints
            SourcePreferenceLevel.SERIES -> config.seriesSourcePreferencePoints
            SourcePreferenceLevel.ENTRY -> config.entrySourcePreferencePoints
        }
    }

    private fun confirmedHistoryPoints(evidence: ConfirmedHistoryEvidence): Double {
        return when (evidence) {
            ConfirmedHistoryEvidence.NONE -> 0.0
            ConfirmedHistoryEvidence.SOURCE -> config.sourceHistoryPoints
            ConfirmedHistoryEvidence.SERIES -> config.seriesHistoryPoints
        }
    }

    private data class NormalizedMatchQuery(
        val title: NormalizedTitle,
        val issue: NormalizedIssueNumber,
        val volume: Int?,
        val year: Int?,
    ) {
        companion object {
            fun from(query: ReadingListMatchQuery): NormalizedMatchQuery {
                val normalizedTitle = TitleNormalizer.normalize(query.seriesTitle)
                return NormalizedMatchQuery(
                    title = normalizedTitle,
                    issue = IssueNumberNormalizer.normalize(query.issueNumber),
                    volume = query.volume ?: normalizedTitle.volume,
                    year = query.year ?: normalizedTitle.year,
                )
            }
        }
    }

    private companion object {
        val CANDIDATE_ORDER = compareByDescending<ScoredReadingListMatchCandidate> {
            it.candidate.userConfirmed
        }
            .thenByDescending { it.score }
            .thenByDescending { it.breakdown.issueEquivalent }
            .thenByDescending { it.breakdown.titleSimilarity }
            .thenByDescending { it.candidate.sourcePreference.ordinal }
            .thenBy { it.candidate.id }
    }
}

private fun evidence(expected: Int?, actual: Int?): EvidenceAgreement {
    return when {
        expected == null || actual == null -> EvidenceAgreement.UNKNOWN
        expected == actual -> EvidenceAgreement.MATCH
        else -> EvidenceAgreement.MISMATCH
    }
}

private fun titleSimilarity(
    expected: NormalizedTitle,
    actual: NormalizedTitle,
): Double {
    if (!expected.isUsable || !actual.isUsable) return 0.0
    if (expected.isEquivalentTo(actual)) return 1.0

    val characterSimilarity = expected.comparisonKeys.maxOf { expectedKey ->
        actual.comparisonKeys.maxOf { actualKey ->
            normalizedLevenshteinSimilarity(expectedKey, actualKey)
        }
    }
    val tokenSimilarity = diceCoefficient(
        expected.articlelessBase.splitToTokenSet(),
        actual.articlelessBase.splitToTokenSet(),
    )

    return (characterSimilarity * CHARACTER_WEIGHT + tokenSimilarity * TOKEN_WEIGHT)
        .coerceIn(0.0, 1.0)
}

private fun normalizedLevenshteinSimilarity(first: String, second: String): Double {
    if (first == second) return 1.0
    if (first.isEmpty() || second.isEmpty()) return 0.0

    val previous = IntArray(second.length + 1) { it }
    val current = IntArray(second.length + 1)

    for (firstIndex in first.indices) {
        current[0] = firstIndex + 1
        for (secondIndex in second.indices) {
            val substitutionCost = if (first[firstIndex] == second[secondIndex]) 0 else 1
            current[secondIndex + 1] = minOf(
                current[secondIndex] + 1,
                previous[secondIndex + 1] + 1,
                previous[secondIndex] + substitutionCost,
            )
        }
        current.copyInto(previous)
    }

    val longestLength = max(first.length, second.length)
    return 1.0 - previous[second.length].toDouble() / longestLength.toDouble()
}

private fun diceCoefficient(first: Set<String>, second: Set<String>): Double {
    if (first.isEmpty() || second.isEmpty()) return 0.0
    val intersectionSize = first.count(second::contains)
    return (2.0 * intersectionSize) / (first.size + second.size).toDouble()
}

private fun String.splitToTokenSet(): Set<String> {
    return if (isBlank()) emptySet() else split(' ').filterTo(linkedSetOf(), String::isNotBlank)
}

private fun Double.roundForDisplay(): Double {
    return round(this * SCORE_PRECISION) / SCORE_PRECISION
}

private const val CHARACTER_WEIGHT = 0.55
private const val TOKEN_WEIGHT = 0.45
private const val SCORE_PRECISION = 100.0
