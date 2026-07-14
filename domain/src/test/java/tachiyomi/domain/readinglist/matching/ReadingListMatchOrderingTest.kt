package tachiyomi.domain.readinglist.matching

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import tachiyomi.domain.readinglist.model.ReadingListEntryResolutionState

@Execution(ExecutionMode.CONCURRENT)
class ReadingListMatchOrderingTest {

    private val scorer = ReadingListMatchScorer()

    @Test
    fun `reading-list source order breaks display ties without bypassing ambiguity`() {
        val result = scorer.decide(
            query = ReadingListMatchQuery(
                seriesTitle = "Example",
                issueNumber = "1",
            ),
            candidates = listOf(
                candidate(id = "later", sourceId = 9, sourceOrder = 1),
                candidate(id = "earlier", sourceId = 7, sourceOrder = 0),
            ),
        )

        result.state shouldBe ReadingListEntryResolutionState.AMBIGUOUS
        result.reason shouldBe MatchDecisionReason.INSUFFICIENT_LEAD
        result.rankedCandidates.map { candidate ->
            candidate.candidate.id
        } shouldContainExactly listOf("earlier", "later")
    }

    @Test
    fun `title similarity is available to series-first search`() {
        scorer.titleSimilarity(
            expectedTitle = "The Batman (2016)",
            actualTitle = "Batman",
        ) shouldBe 1.0
    }

    private fun candidate(
        id: String,
        sourceId: Long,
        sourceOrder: Int,
    ): ReadingListMatchCandidate {
        return ReadingListMatchCandidate(
            id = id,
            sourceId = sourceId,
            sourceOrder = sourceOrder,
            seriesTitle = "Example",
            issueNumber = "1",
            sourcePreference = SourcePreferenceLevel.READING_LIST,
        )
    }
}
