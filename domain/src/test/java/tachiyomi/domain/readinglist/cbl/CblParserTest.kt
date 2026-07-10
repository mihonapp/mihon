package tachiyomi.domain.readinglist.cbl

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import nl.adaptivity.xmlutil.ExperimentalXmlUtilApi
import nl.adaptivity.xmlutil.core.KtXmlReader
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import tachiyomi.domain.readinglist.cbl.model.CblParseException
import tachiyomi.domain.readinglist.cbl.model.CblParseFailure
import tachiyomi.domain.readinglist.cbl.model.CblParseWarningCode
import tachiyomi.domain.readinglist.cbl.model.CblParserLimits

@OptIn(ExperimentalXmlUtilApi::class)
@Execution(ExecutionMode.CONCURRENT)
class CblParserTest {

    private val parser = testParser()

    @Test
    fun `preserves CBL book order and metadata`() {
        val result = parser.parse(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <ReadingList Source="fixture">
                <Name>2006 - 52</Name>
                <Description>Weekly series</Description>
                <NumIssues>2</NumIssues>
                <Books>
                    <Book Series="52" Number="1" Volume="2006" Year="2006" Format="Single Issue">
                        <Database Name="cv" Series="18053" Issue="105733" Provider="ComicVine" />
                        <Edition>Direct</Edition>
                    </Book>
                    <Book Series="52" Number="2" Volume="2006" Year="2006">
                        <Database Name="cv" Series="18053" Issue="106101" />
                    </Book>
                </Books>
            </ReadingList>
            """.trimIndent(),
        )

        result.name shouldBe "2006 - 52"
        result.description shouldBe "Weekly series"
        result.declaredIssueCount shouldBe 2
        result.extraAttributes shouldBe mapOf("Source" to "fixture")
        result.warnings shouldHaveSize 0

        result.books.map { it.position } shouldContainExactly listOf(0, 1)
        result.books.map { it.number } shouldContainExactly listOf("1", "2")

        val first = result.books.first()
        first.series shouldBe "52"
        first.volume shouldBe "2006"
        first.year shouldBe "2006"
        first.extraAttributes shouldBe mapOf("Format" to "Single Issue")
        first.extraElements shouldBe mapOf("Edition" to listOf("Direct"))
        first.databases.single().name shouldBe "cv"
        first.databases.single().seriesId shouldBe "18053"
        first.databases.single().issueId shouldBe "105733"
        first.databases.single().extraAttributes shouldBe mapOf("Provider" to "ComicVine")
    }

    @Test
    fun `allows missing optional book and database metadata`() {
        val result = parser.parse(
            """
            <ReadingList>
                <Name>Minimal</Name>
                <Books>
                    <Book Series="Example" Number="1" />
                </Books>
            </ReadingList>
            """.trimIndent(),
        )

        result.books.single().volume shouldBe null
        result.books.single().year shouldBe null
        result.books.single().databases shouldHaveSize 0
    }

    @Test
    fun `reports non-fatal list metadata warnings`() {
        val result = parser.parse(
            """
            <ReadingList>
                <NumIssues>not-a-number</NumIssues>
                <Books />
            </ReadingList>
            """.trimIndent(),
        )

        result.warnings.map { it.code } shouldContainExactly listOf(
            CblParseWarningCode.INVALID_DECLARED_ISSUE_COUNT,
            CblParseWarningCode.MISSING_NAME,
            CblParseWarningCode.EMPTY_LIST,
        )
    }

    @Test
    fun `reports declared issue count mismatch`() {
        val result = parser.parse(
            """
            <ReadingList>
                <Name>Mismatch</Name>
                <NumIssues>2</NumIssues>
                <Books>
                    <Book Series="Example" Number="1" />
                </Books>
            </ReadingList>
            """.trimIndent(),
        )

        result.warnings.map { it.code } shouldContainExactly listOf(
            CblParseWarningCode.ISSUE_COUNT_MISMATCH,
        )
    }

    @Test
    fun `rejects DTD and entity declarations`() {
        val error = assertThrows(CblParseException::class.java) {
            parser.parse(
                """
                <!DOCTYPE ReadingList [
                    <!ENTITY secret SYSTEM "file:///etc/passwd">
                ]>
                <ReadingList>
                    <Name>&secret;</Name>
                    <Books />
                </ReadingList>
                """.trimIndent(),
            )
        }

        error.failure shouldBe CblParseFailure.UNSAFE_XML
    }

    @Test
    fun `rejects malformed XML without returning a partial list`() {
        val error = assertThrows(CblParseException::class.java) {
            parser.parse(
                """
                <ReadingList>
                    <Name>Broken</Name>
                    <Books>
                        <Book Series="Example" Number="1">
                    </Books>
                </ReadingList>
                """.trimIndent(),
            )
        }

        error.failure shouldBe CblParseFailure.MALFORMED_XML
    }

    @Test
    fun `rejects a root other than ReadingList`() {
        val error = assertThrows(CblParseException::class.java) {
            parser.parse("<ComicInfo />")
        }

        error.failure shouldBe CblParseFailure.INVALID_ROOT
    }

    @Test
    fun `rejects books without matching attributes`() {
        val missingSeries = assertThrows(CblParseException::class.java) {
            parser.parse(
                """
                <ReadingList>
                    <Books>
                        <Book Number="1" />
                    </Books>
                </ReadingList>
                """.trimIndent(),
            )
        }
        val missingNumber = assertThrows(CblParseException::class.java) {
            parser.parse(
                """
                <ReadingList>
                    <Books>
                        <Book Series="Example" />
                    </Books>
                </ReadingList>
                """.trimIndent(),
            )
        }

        missingSeries.failure shouldBe CblParseFailure.MISSING_BOOK_ATTRIBUTE
        missingNumber.failure shouldBe CblParseFailure.MISSING_BOOK_ATTRIBUTE
    }

    @Test
    fun `enforces configured input and entry limits`() {
        val characterLimitedParser = testParser(CblParserLimits(maxCharacters = 20, maxBooks = 10))
        val bookLimitedParser = testParser(CblParserLimits(maxCharacters = 10_000, maxBooks = 1))

        val tooLarge = assertThrows(CblParseException::class.java) {
            characterLimitedParser.parse("<ReadingList><Books /></ReadingList>")
        }
        val tooManyBooks = assertThrows(CblParseException::class.java) {
            bookLimitedParser.parse(
                """
                <ReadingList>
                    <Books>
                        <Book Series="Example" Number="1" />
                        <Book Series="Example" Number="2" />
                    </Books>
                </ReadingList>
                """.trimIndent(),
            )
        }

        tooLarge.failure shouldBe CblParseFailure.INPUT_TOO_LARGE
        tooManyBooks.failure shouldBe CblParseFailure.TOO_MANY_BOOKS
    }

    private fun testParser(limits: CblParserLimits = CblParserLimits()) = CblParser(
        readerFactory = { xml -> KtXmlReader(xml) },
        limits = limits,
    )
}
