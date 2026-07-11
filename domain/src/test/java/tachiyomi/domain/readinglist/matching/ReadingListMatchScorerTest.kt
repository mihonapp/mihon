package tachiyomi.domain.readinglist.matching

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import tachiyomi.domain.readinglist.model.ReadingListEntryResolutionState

@Execution(ExecutionMode.CONCURRENT)
class ReadingListMatchScorerTest {

    private val scorer = ReadingListMatchScorer()
    private val query = ReadingListMatchQuery(
        seriesTitle = "The Amazing Spider-Man",
        issueNumber = "#001",
    )

    @Test
    fun `exact title and issue meet the default automatic threshold`() {
        val result = scorer.decide(
            query = query,
            candidates = listOf(candidate(id = "exact")),
        )

        result.state shouldBe ReadingListEntryResolutionState.AUTO_MATCHED
        result.reason shouldBe MatchDecisionReason.AUTO_ACCEPTED
        result.leadingCandidate?.score shouldBe 88.0
        result.acceptedCandidate?.candidate?.id shouldBe "exact"
        result.leadOverRunnerUp shouldBe null
    }

    @Test
    fun `title edition metadata supplies separate year evidence`() {
        val result = scorer.score(
            query = ReadingListMatchQuery(
                seriesTitle = "Batman (2016)",
                issueNumber = "1",
            ),
            candidate = ReadingListMatchCandidate(
                id = "batman",
                sourceId = 1,
                seriesTitle = "Batman",
                issueNumber = "Issue 01",
                year = 2016,
            ),
        )

        result.breakdown.titleSimilarity shouldBe 1.0
        result.breakdown.issueEquivalent shouldBe true
        result.breakdown.yearEvidence shouldBe EvidenceAgreement.MATCH
        result.breakdown.yearPoints shouldBe 4.0
        result.score shouldBe 92.0
    }

    @Test
    fun `a ten point lead is sufficient for automatic acceptance`() {
        val result = scorer.decide(
            query = query.copy(year = 2016),
            candidates = listOf(
                candidate(id = "winner", year = 2016),
                candidate(id = "runner-up", year = 2011),
            ),
        )

        result.state shouldBe ReadingListEntryResolutionState.AUTO_MATCHED
        result.reason shouldBe MatchDecisionReason.AUTO_ACCEPTED
        result.leadingCandidate?.candidate?.id shouldBe "winner"
        result.leadOverRunnerUp shouldBe 10.0
    }

    @Test
    fun `equal high scoring candidates remain ambiguous`() {
        val result = scorer.decide(
            query = query,
            candidates = listOf(
                candidate(id = "alpha"),
                candidate(id = "beta"),
            ),
        )

        result.state shouldBe ReadingListEntryResolutionState.AMBIGUOUS
        result.reason shouldBe MatchDecisionReason.INSUFFICIENT_LEAD
        result.leadOverRunnerUp shouldBe 0.0
        result.acceptedCandidate shouldBe null
        result.rankedCandidates.map { it.candidate.id } shouldContainExactly listOf("alpha", "beta")
    }

    @Test
    fun `metadata mismatch keeps an otherwise exact candidate in review`() {
        val result = scorer.decide(
            query = query.copy(year = 2016),
            candidates = listOf(candidate(id = "wrong-year", year = 2011)),
        )

        result.leadingCandidate?.score shouldBe 82.0
        result.state shouldBe ReadingListEntryResolutionState.AMBIGUOUS
        result.reason shouldBe MatchDecisionReason.BELOW_AUTO_THRESHOLD
    }

    @Test
    fun `weak candidates remain unresolved`() {
        val result = scorer.decide(
            query = query,
            candidates = listOf(
                candidate(
                    id = "unrelated",
                    seriesTitle = "Superman",
                    issueNumber = "27",
                ),
            ),
        )

        result.state shouldBe ReadingListEntryResolutionState.UNRESOLVED
        result.reason shouldBe MatchDecisionReason.BELOW_REVIEW_THRESHOLD
        result.acceptedCandidate shouldBe null
    }

    @Test
    fun `source preferences follow the configured hierarchy`() {
        val scores = SourcePreferenceLevel.entries.associateWith { level ->
            scorer.score(
                query = query,
                candidate = candidate(
                    id = level.name,
                    sourcePreference = level,
                ),
            ).score
        }

        scores[SourcePreferenceLevel.NONE] shouldBe 88.0
        scores[SourcePreferenceLevel.GLOBAL] shouldBe 88.5
        scores[SourcePreferenceLevel.READING_LIST] shouldBe 89.0
        scores[SourcePreferenceLevel.SERIES] shouldBe 90.0
        scores[SourcePreferenceLevel.ENTRY] shouldBe 91.0
    }

    @Test
    fun `score breakdown exposes external and history evidence`() {
        val result = scorer.score(
            query = query.copy(year = 2016, volume = 4),
            candidate = candidate(
                id = "evidence",
                year = 2016,
                volume = 4,
                externalIdentifierEvidence = EvidenceAgreement.MATCH,
                confirmedHistory = ConfirmedHistoryEvidence.SERIES,
            ),
        )

        result.breakdown.yearEvidence shouldBe EvidenceAgreement.MATCH
        result.breakdown.volumeEvidence shouldBe EvidenceAgreement.MATCH
        result.breakdown.externalIdentifierPoints shouldBe 4.0
        result.breakdown.confirmedHistoryPoints shouldBe 3.0
        result.score shouldBe 100.0
    }

    @Test
    fun `external identifier mismatch is a penalty rather than silent evidence`() {
        val matching = scorer.score(
            query = query,
            candidate = candidate(
                id = "match",
                externalIdentifierEvidence = EvidenceAgreement.MATCH,
            ),
        )
        val mismatching = scorer.score(
            query = query,
            candidate = candidate(
                id = "mismatch",
                externalIdentifierEvidence = EvidenceAgreement.MISMATCH,
            ),
        )

        matching.score shouldBe 92.0
        mismatching.score shouldBe 80.0
        mismatching.breakdown.externalIdentifierPoints shouldBe -8.0
    }

    @Test
    fun `issue mismatch blocks automatic acceptance even with supporting evidence`() {
        val permissiveScorer = ReadingListMatchScorer(
            MatchScoringConfig(
                autoAcceptThreshold = 65.0,
                reviewThreshold = 50.0,
            ),
        )
        val result = permissiveScorer.decide(
            query = query.copy(year = 2016, volume = 1),
            candidates = listOf(
                candidate(
                    id = "wrong-issue",
                    issueNumber = "2",
                    year = 2016,
                    volume = 1,
                    sourcePreference = SourcePreferenceLevel.ENTRY,
                    externalIdentifierEvidence = EvidenceAgreement.MATCH,
                    confirmedHistory = ConfirmedHistoryEvidence.SERIES,
                ),
            ),
        )

        result.leadingCandidate?.score shouldBe 76.0
        result.state shouldBe ReadingListEntryResolutionState.AMBIGUOUS
        result.reason shouldBe MatchDecisionReason.ISSUE_MISMATCH
    }

    @Test
    fun `weak title similarity blocks automatic acceptance`() {
        val permissiveScorer = ReadingListMatchScorer(
            MatchScoringConfig(
                autoAcceptThreshold = 50.0,
                reviewThreshold = 40.0,
            ),
        )
        val result = permissiveScorer.decide(
            query = ReadingListMatchQuery("Batman", "1"),
            candidates = listOf(
                ReadingListMatchCandidate(
                    id = "beyond",
                    sourceId = 1,
                    seriesTitle = "Batman Beyond",
                    issueNumber = "1",
                ),
            ),
        )

        result.state shouldBe ReadingListEntryResolutionState.AMBIGUOUS
        result.reason shouldBe MatchDecisionReason.TITLE_TOO_WEAK
    }

    @Test
    fun `user confirmed candidate overrides automatic ranking`() {
        val result = scorer.decide(
            query = query,
            candidates = listOf(
                candidate(id = "automatic"),
                candidate(
                    id = "confirmed",
                    seriesTitle = "Different title",
                    issueNumber = "99",
                    userConfirmed = true,
                ),
            ),
        )

        result.state shouldBe ReadingListEntryResolutionState.USER_CONFIRMED
        result.reason shouldBe MatchDecisionReason.USER_CONFIRMED
        result.acceptedCandidate?.candidate?.id shouldBe "confirmed"
        result.rankedCandidates.first().candidate.userConfirmed shouldBe true
    }

    @Test
    fun `multiple user confirmed candidates are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            scorer.decide(
                query = query,
                candidates = listOf(
                    candidate(id = "first", userConfirmed = true),
                    candidate(id = "second", userConfirmed = true),
                ),
            )
        }
    }

    @Test
    fun `no candidates is unresolved without a synthetic score`() {
        val result = scorer.decide(query, emptyList())

        result.state shouldBe ReadingListEntryResolutionState.UNRESOLVED
        result.reason shouldBe MatchDecisionReason.NO_CANDIDATES
        result.leadingCandidate shouldBe null
        result.leadOverRunnerUp shouldBe null
    }

    private fun candidate(
        id: String,
        seriesTitle: String = "Amazing Spider Man",
        issueNumber: String = "1",
        volume: Int? = null,
        year: Int? = null,
        sourcePreference: SourcePreferenceLevel = SourcePreferenceLevel.NONE,
        externalIdentifierEvidence: EvidenceAgreement = EvidenceAgreement.UNKNOWN,
        confirmedHistory: ConfirmedHistoryEvidence = ConfirmedHistoryEvidence.NONE,
        userConfirmed: Boolean = false,
    ): ReadingListMatchCandidate {
        return ReadingListMatchCandidate(
            id = id,
            sourceId = 1,
            seriesTitle = seriesTitle,
            issueNumber = issueNumber,
            volume = volume,
            year = year,
            sourcePreference = sourcePreference,
            externalIdentifierEvidence = externalIdentifierEvidence,
            confirmedHistory = confirmedHistory,
            userConfirmed = userConfirmed,
        )
    }
}
