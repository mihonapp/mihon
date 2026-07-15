package tachiyomi.domain.readinglist.matching

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ReadingListSeriesYearEvidenceTest {

    private val scorer = ReadingListMatchScorer()

    @Test
    fun `parenthetical title year matches year-like cbl volume without issue-year conflict`() {
        val result = scorer.score(
            query = ReadingListMatchQuery(
                seriesTitle = "Watchmen",
                issueNumber = "5",
                volume = 1986,
                year = 1987,
            ),
            candidate = ReadingListMatchCandidate(
                id = "watchmen",
                sourceId = 1,
                seriesTitle = "Watchmen (1986)",
                issueNumber = "5",
                year = 1986,
            ),
        )

        result.breakdown.titleSimilarity shouldBe 1.0
        result.breakdown.issueEquivalent shouldBe true
        result.breakdown.yearEvidence shouldBe EvidenceAgreement.UNKNOWN
        result.breakdown.yearPoints shouldBe 0.0
        result.breakdown.volumeEvidence shouldBe EvidenceAgreement.MATCH
        result.breakdown.volumePoints shouldBe 4.0
        result.score shouldBe 92.0
    }

    @Test
    fun `explicit issue year remains independent when it differs from parenthetical series year`() {
        val result = scorer.score(
            query = ReadingListMatchQuery(
                seriesTitle = "Watchmen",
                issueNumber = "5",
                volume = 1986,
                year = 1987,
            ),
            candidate = ReadingListMatchCandidate(
                id = "watchmen-dated",
                sourceId = 1,
                seriesTitle = "Watchmen (1986)",
                issueNumber = "5",
                year = 1987,
            ),
        )

        result.breakdown.yearEvidence shouldBe EvidenceAgreement.MATCH
        result.breakdown.volumeEvidence shouldBe EvidenceAgreement.MATCH
        result.score shouldBe 96.0
    }

    @Test
    fun `different parenthetical series year remains conflicting edition evidence`() {
        val result = scorer.score(
            query = ReadingListMatchQuery(
                seriesTitle = "Watchmen",
                issueNumber = "5",
                volume = 1986,
                year = 1987,
            ),
            candidate = ReadingListMatchCandidate(
                id = "watchmen-wrong-edition",
                sourceId = 1,
                seriesTitle = "Watchmen (2019)",
                issueNumber = "5",
                year = 2019,
            ),
        )

        result.breakdown.yearEvidence shouldBe EvidenceAgreement.UNKNOWN
        result.breakdown.volumeEvidence shouldBe EvidenceAgreement.MISMATCH
        result.score shouldBe 82.0
    }
}
