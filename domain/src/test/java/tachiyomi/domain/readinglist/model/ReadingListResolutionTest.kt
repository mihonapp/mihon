package tachiyomi.domain.readinglist.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import tachiyomi.domain.readinglist.matching.ConfirmedHistoryEvidence
import tachiyomi.domain.readinglist.matching.EvidenceAgreement
import tachiyomi.domain.readinglist.matching.MatchDecisionReason
import tachiyomi.domain.readinglist.matching.MatchScoreBreakdown
import tachiyomi.domain.readinglist.matching.SourcePreferenceLevel

@Execution(ExecutionMode.CONCURRENT)
class ReadingListResolutionTest {

    @Test
    fun `candidate score is sourced from its persisted breakdown`() {
        candidate().score shouldBe 92.0
    }

    @Test
    fun `candidate identity rejects blank stable IDs`() {
        assertThrows(IllegalArgumentException::class.java) {
            ReadingListCandidateIdentity(
                sourceId = 7,
                candidateId = " ",
            )
        }
    }

    @Test
    fun `chapter entry overrides require a manga override`() {
        assertThrows(IllegalArgumentException::class.java) {
            ReadingListEntryOverrideUpdate(
                sourceId = 7,
                mangaUrl = null,
                chapterUrl = "/chapter/1",
            )
        }
    }

    @Test
    fun `automatic matches require an accepted candidate`() {
        assertThrows(IllegalArgumentException::class.java) {
            ReadingListAutomaticResolutionUpdate(
                state = ReadingListEntryResolutionState.AUTO_MATCHED,
                leadingConfidence = 92.0,
                matcherVersion = 1,
                acceptedCandidate = null,
            )
        }
    }

    @Test
    fun `ambiguous results cannot persist an accepted candidate`() {
        assertThrows(IllegalArgumentException::class.java) {
            ReadingListAutomaticResolutionUpdate(
                state = ReadingListEntryResolutionState.AMBIGUOUS,
                leadingConfidence = 92.0,
                matcherVersion = 1,
                acceptedCandidate = candidate(),
            )
        }
    }

    @Test
    fun `automatic match accepts a consistent candidate`() {
        val candidate = candidate()

        ReadingListAutomaticResolutionUpdate(
            state = ReadingListEntryResolutionState.AUTO_MATCHED,
            leadingConfidence = candidate.score,
            matcherVersion = candidate.matcherVersion,
            acceptedCandidate = candidate,
        ).state shouldBe ReadingListEntryResolutionState.AUTO_MATCHED
    }

    private fun candidate(): ReadingListMatchCandidateSnapshot {
        return ReadingListMatchCandidateSnapshot(
            identity = ReadingListCandidateIdentity(
                sourceId = 7,
                candidateId = "series/1#chapter/1",
            ),
            sourceName = "Fixture",
            sourceLanguage = "en",
            mangaUrl = "/series/1",
            chapterUrl = "/chapter/1",
            seriesTitle = "Example",
            issueNumber = "1",
            volume = 1,
            year = 2026,
            breakdown = breakdown(total = 92.0),
            decisionReason = MatchDecisionReason.AUTO_ACCEPTED,
            leadOverRunnerUp = 12.0,
            matcherVersion = 1,
        )
    }

    private fun breakdown(total: Double): MatchScoreBreakdown {
        return MatchScoreBreakdown(
            titleSimilarity = 1.0,
            titlePoints = 58.0,
            issueEquivalent = true,
            issuePoints = 30.0,
            yearEvidence = EvidenceAgreement.MATCH,
            yearPoints = 4.0,
            volumeEvidence = EvidenceAgreement.UNKNOWN,
            volumePoints = 0.0,
            externalIdentifierEvidence = EvidenceAgreement.UNKNOWN,
            externalIdentifierPoints = 0.0,
            sourcePreference = SourcePreferenceLevel.NONE,
            sourcePreferencePoints = 0.0,
            confirmedHistory = ConfirmedHistoryEvidence.NONE,
            confirmedHistoryPoints = 0.0,
            total = total,
        )
    }
}
