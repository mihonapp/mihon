package tachiyomi.data.readinglist

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import tachiyomi.domain.readinglist.cbl.model.CblBook
import tachiyomi.domain.readinglist.cbl.model.CblParseWarning
import tachiyomi.domain.readinglist.cbl.model.CblParseWarningCode
import tachiyomi.domain.readinglist.cbl.model.CblReadingList

@Execution(ExecutionMode.CONCURRENT)
class ReadingListStorageCodecTest {

    private val codec = ReadingListStorageCodec(
        Json {
            explicitNulls = false
        },
    )

    @Test
    fun `round trips original CBL metadata`() {
        val attributes = linkedMapOf(
            "Source" to "fixture",
            "Language" to "日本語",
        )
        val elements = linkedMapOf(
            "Notes" to listOf("First", "Second"),
            "Empty" to listOf(""),
        )

        codec.decodeAttributes(codec.encodeAttributes(attributes)) shouldBe attributes
        codec.decodeElements(codec.encodeElements(elements)) shouldBe elements
    }

    @Test
    fun `round trips parser warnings`() {
        val warnings = listOf(
            CblParseWarning(
                code = CblParseWarningCode.MISSING_NAME,
                message = "The reading list has no name",
            ),
            CblParseWarning(
                code = CblParseWarningCode.ISSUE_COUNT_MISMATCH,
                message = "Expected 3 but found 2",
            ),
        )

        codec.decodeWarnings(codec.encodeWarnings(warnings)) shouldBe warnings
    }

    @Test
    fun `accepts contiguous authoritative positions`() {
        val readingList = readingListWithPositions(0, 1, 2)

        assertDoesNotThrow {
            readingList.requireValidPersistenceOrder()
        }
    }

    @Test
    fun `rejects gaps or reordered positions`() {
        val readingList = readingListWithPositions(0, 2, 1)

        assertThrows(IllegalArgumentException::class.java) {
            readingList.requireValidPersistenceOrder()
        }
    }

    @Test
    fun `rejects blank matching identifiers`() {
        val readingList = CblReadingList(
            name = "Invalid",
            description = null,
            declaredIssueCount = 1,
            books = listOf(
                CblBook(
                    position = 0,
                    series = " ",
                    number = "1",
                    volume = null,
                    year = null,
                ),
            ),
        )

        assertThrows(IllegalArgumentException::class.java) {
            readingList.requireValidPersistenceOrder()
        }
    }

    private fun readingListWithPositions(vararg positions: Int): CblReadingList {
        return CblReadingList(
            name = "Fixture",
            description = null,
            declaredIssueCount = positions.size,
            books = positions.mapIndexed { index, position ->
                CblBook(
                    position = position,
                    series = "Series $index",
                    number = (index + 1).toString(),
                    volume = null,
                    year = null,
                )
            },
        )
    }
}
