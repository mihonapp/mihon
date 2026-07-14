package tachiyomi.domain.readinglist.matching

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
class ReadingListSeriesKeyTest {

    @Test
    fun `equivalent titles without edition metadata share one series key`() {
        ReadingListSeriesKey.from("The Batman") shouldBe
            ReadingListSeriesKey.from("Batman")
    }

    @Test
    fun `title metadata and explicit metadata produce the same key`() {
        ReadingListSeriesKey.from("The Batman (2016)") shouldBe
            ReadingListSeriesKey.from(
                seriesTitle = "Batman",
                year = "2016",
            )
    }

    @Test
    fun `different publication years keep separate series keys`() {
        ReadingListSeriesKey.from("Batman (1940)") shouldBe
            "batman|year=1940"
        ReadingListSeriesKey.from("Batman (2016)") shouldBe
            "batman|year=2016"
        ReadingListSeriesKey.from("Batman (1940)") shouldBe
            ReadingListSeriesKey.from(
                seriesTitle = "Batman",
                year = "01940",
            )
    }

    @Test
    fun `different volumes keep separate series keys`() {
        ReadingListSeriesKey.from(
            seriesTitle = "Example",
            volume = "1",
        ) shouldBe "example|volume=1"
        ReadingListSeriesKey.from(
            seriesTitle = "Example",
            volume = "2",
        ) shouldBe "example|volume=2"
    }

    @Test
    fun `different series keep different keys`() {
        ReadingListSeriesKey.from("Batman") shouldBe "batman"
        ReadingListSeriesKey.from("Batman Beyond") shouldBe "batman beyond"
    }
}
