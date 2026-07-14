package tachiyomi.domain.readinglist.matching

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import tachiyomi.domain.readinglist.normalization.IssueNumberNormalizer

@Execution(ExecutionMode.CONCURRENT)
class ReadingListChapterIssueExtractorTest {

    @Test
    fun `extracts a labeled issue after the series title`() {
        val issue = ReadingListChapterIssueExtractor.extract(
            seriesTitle = "The Amazing Spider-Man",
            chapterName = "The Amazing Spider-Man #001 - New Beginnings",
            chapterNumber = -1.0f,
            expectedIssue = "1",
        )

        IssueNumberNormalizer.normalize(issue.orEmpty()).canonical shouldBe "1"
    }

    @Test
    fun `preserves annual issue identity`() {
        val issue = ReadingListChapterIssueExtractor.extract(
            seriesTitle = "Batman",
            chapterName = "Batman Vol. 1 Annual #2",
            chapterNumber = 2.0f,
            expectedIssue = "Annual 2",
        )

        IssueNumberNormalizer.normalize(issue.orEmpty()).canonical shouldBe "annual 2"
    }

    @Test
    fun `annual issue does not collapse to the regular chapter fallback`() {
        val issue = ReadingListChapterIssueExtractor.extract(
            seriesTitle = "Batman",
            chapterName = "Batman Vol. 1 Annual #2",
            chapterNumber = 2.0f,
            expectedIssue = "2",
        )

        val normalized = IssueNumberNormalizer.normalize(issue.orEmpty())
        normalized.canonical shouldBe "annual 2"
        normalized.isEquivalentTo(IssueNumberNormalizer.normalize("2")) shouldBe false
    }

    @Test
    fun `suffix issue does not collapse to the regular chapter fallback`() {
        val issue = ReadingListChapterIssueExtractor.extract(
            seriesTitle = "Example",
            chapterName = "Example #12A",
            chapterNumber = 12.0f,
            expectedIssue = "12",
        )

        val normalized = IssueNumberNormalizer.normalize(issue.orEmpty())
        normalized.canonical shouldBe "12a"
        normalized.isEquivalentTo(IssueNumberNormalizer.normalize("12")) shouldBe false
    }

    @Test
    fun `preserves issue-kind markers in additional chapter-name positions`() {
        listOf(
            Triple("Chapter 1 - Annual", "Annual 1", "annual 1"),
            Triple("Example - Special #3", "Special 3", "special 3"),
            Triple("Example Vol. 2 FCBD 2024", "FCBD 2024", "fcbd 2024"),
        ).forEach { (chapterName, expectedIssue, canonical) ->
            val issue = ReadingListChapterIssueExtractor.extract(
                seriesTitle = "Example",
                chapterName = chapterName,
                chapterNumber = IssueNumberNormalizer.normalize(expectedIssue).numericValue?.toFloat() ?: -1.0f,
                expectedIssue = expectedIssue,
            )

            IssueNumberNormalizer.normalize(issue.orEmpty()).canonical shouldBe canonical
        }
    }

    @Test
    fun `preserves fractional issue identity`() {
        val issue = ReadingListChapterIssueExtractor.extract(
            seriesTitle = "Legacy",
            chapterName = "Legacy 1½",
            chapterNumber = 1.5f,
            expectedIssue = "1 1/2",
        )

        IssueNumberNormalizer.normalize(issue.orEmpty()).canonical shouldBe "1.5"
    }

    @Test
    fun `preserves suffix issue identity`() {
        val issue = ReadingListChapterIssueExtractor.extract(
            seriesTitle = "Example",
            chapterName = "Example #12A",
            chapterNumber = 12.0f,
            expectedIssue = "12a",
        )

        IssueNumberNormalizer.normalize(issue.orEmpty()).canonical shouldBe "12a"
    }

    @Test
    fun `matching source chapter number beats an incidental trailing year`() {
        val issue = ReadingListChapterIssueExtractor.extract(
            seriesTitle = "Example",
            chapterName = "Example - Remastered 2024",
            chapterNumber = 1.0f,
            expectedIssue = "1",
        )

        issue shouldBe "1"
    }

    @Test
    fun `special edition text does not become a special issue`() {
        val issue = ReadingListChapterIssueExtractor.extract(
            seriesTitle = "Example",
            chapterName = "Example Special Edition #12",
            chapterNumber = 12.0f,
            expectedIssue = "12",
        )

        IssueNumberNormalizer.normalize(issue.orEmpty()).canonical shouldBe "12"
    }

    @Test
    fun `uses the source chapter number as a fallback`() {
        val issue = ReadingListChapterIssueExtractor.extract(
            seriesTitle = "Example",
            chapterName = "A title without a visible issue",
            chapterNumber = 0.0f,
            expectedIssue = "0",
        )

        issue shouldBe "0"
    }

    @Test
    fun `keeps opaque issue names when they match`() {
        val issue = ReadingListChapterIssueExtractor.extract(
            seriesTitle = "Example",
            chapterName = "Example Prologue",
            chapterNumber = -1.0f,
            expectedIssue = "Prologue",
        )

        IssueNumberNormalizer.normalize(issue.orEmpty()).canonical shouldBe "prologue"
    }
}
