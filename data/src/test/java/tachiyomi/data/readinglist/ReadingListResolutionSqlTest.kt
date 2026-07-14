package tachiyomi.data.readinglist

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import tachiyomi.data.Database
import tachiyomi.data.Reading_listsQueries

@Execution(ExecutionMode.CONCURRENT)
class ReadingListResolutionSqlTest {

    @Test
    fun `automatic resolution cannot overwrite a confirmed entry`() = runTest {
        withQueries { _, queries ->
            val fixture = insertFixture(queries)

            queries.confirmResolution(
                matchedSourceId = 7,
                matchedMangaUrl = "/confirmed-series",
                matchedChapterUrl = "/confirmed-chapter",
                confidence = 92.0,
                matcherVersion = 1,
                entryId = fixture.entryId,
            ) shouldBe 1L

            queries.applyAutomaticResolution(
                resolutionState = "AUTO_MATCHED",
                matchedSourceId = 9,
                matchedMangaUrl = "/automatic-series",
                matchedChapterUrl = "/automatic-chapter",
                confidence = 99.0,
                matcherVersion = 2,
                entryId = fixture.entryId,
            ) shouldBe 0L

            val entry = queries.getReadingListEntries(fixture.readingListId).awaitAsOne()
            entry.resolutionState shouldBe "USER_CONFIRMED"
            entry.matchedSourceId shouldBe 7L
            entry.matchedMangaUrl shouldBe "/confirmed-series"
            entry.matchedChapterUrl shouldBe "/confirmed-chapter"
            entry.confidence shouldBe 92.0
            entry.matcherVersion shouldBe 1L
            entry.userConfirmed shouldBe true
        }
    }

    @Test
    fun `automatic resolution preserves explicit skipped state`() = runTest {
        withQueries { driver, queries ->
            val fixture = insertFixture(queries)
            driver.execute(
                null,
                "UPDATE reading_list_entries SET skipped = 1 WHERE _id = ?",
                1,
            ) {
                bindLong(0, fixture.entryId)
            }.await()

            queries.applyAutomaticResolution(
                resolutionState = "UNRESOLVED",
                matchedSourceId = null,
                matchedMangaUrl = null,
                matchedChapterUrl = null,
                confidence = 40.0,
                matcherVersion = 2,
                entryId = fixture.entryId,
            ) shouldBe 1L

            val entry = queries.getReadingListEntries(fixture.readingListId).awaitAsOne()
            entry.skipped shouldBe true
            entry.resolutionState shouldBe "UNRESOLVED"
        }
    }

    @Test
    fun `automatic series mappings cannot replace confirmed mappings`() = runTest {
        withQueries { _, queries ->
            val fixture = insertFixture(queries)

            queries.applyAutomaticSeriesMapping(
                readingListId = fixture.readingListId,
                seriesKey = "example",
                seriesTitle = "Example",
                sourceId = 1,
                mangaUrl = "/automatic-one",
                createdAt = 10,
                updatedAt = 10,
            ) shouldBe 1L

            queries.confirmSeriesMapping(
                readingListId = fixture.readingListId,
                seriesKey = "example",
                seriesTitle = "Example",
                sourceId = 2,
                mangaUrl = "/confirmed",
                createdAt = 20,
                updatedAt = 20,
            ) shouldBe 1L

            queries.applyAutomaticSeriesMapping(
                readingListId = fixture.readingListId,
                seriesKey = "example",
                seriesTitle = "Example",
                sourceId = 3,
                mangaUrl = "/automatic-two",
                createdAt = 30,
                updatedAt = 30,
            ) shouldBe 0L

            val mapping = queries.getSeriesMappingsByReadingListId(
                fixture.readingListId,
            ).awaitAsOne()
            mapping.sourceId shouldBe 2L
            mapping.mangaUrl shouldBe "/confirmed"
            mapping.userConfirmed shouldBe true
            mapping.createdAt shouldBe 10L
            mapping.updatedAt shouldBe 20L
        }
    }

    @Test
    fun `deleting a list cascades through resolution storage`() = runTest {
        withQueries { driver, queries ->
            val fixture = insertFixture(queries)

            queries.insertMatchCandidate(
                entryId = fixture.entryId,
                sourceId = 1,
                candidateId = "candidate",
                sourceName = "Fixture",
                sourceLanguage = "en",
                mangaUrl = "/series",
                chapterUrl = "/chapter",
                seriesTitle = "Example",
                issueNumber = "1",
                volume = null,
                year = null,
                score = 88.0,
                titleSimilarity = 1.0,
                issueEquivalent = true,
                scoreBreakdown = "{}",
                decisionReason = "AUTO_ACCEPTED",
                leadOverRunnerUp = null,
                matcherVersion = 1,
                createdAt = 1,
                updatedAt = 1,
            )
            queries.insertCandidateRejection(
                entryId = fixture.entryId,
                sourceId = 1,
                candidateId = "candidate",
                mangaUrl = "/series",
                chapterUrl = "/chapter",
                rejectedAt = 2,
            )
            queries.upsertEntryOverride(
                entryId = fixture.entryId,
                sourceId = 1,
                mangaUrl = "/series",
                chapterUrl = "/chapter",
                createdAt = 3,
                updatedAt = 3,
            )
            queries.confirmSeriesMapping(
                readingListId = fixture.readingListId,
                seriesKey = "example",
                seriesTitle = "Example",
                sourceId = 1,
                mangaUrl = "/series",
                createdAt = 4,
                updatedAt = 4,
            )

            queries.deleteReadingList(fixture.readingListId)

            countRows(driver, "reading_list_match_candidates") shouldBe 0L
            countRows(driver, "reading_list_candidate_rejections") shouldBe 0L
            countRows(driver, "reading_list_entry_overrides") shouldBe 0L
            countRows(driver, "reading_list_series_mappings") shouldBe 0L
            queries.getMatchCandidatesByReadingListId(
                fixture.readingListId,
            ).awaitAsList() shouldBe emptyList()
            queries.getCandidateRejectionsByReadingListId(
                fixture.readingListId,
            ).awaitAsList() shouldBe emptyList()
            queries.getEntryOverridesByReadingListId(
                fixture.readingListId,
            ).awaitAsList() shouldBe emptyList()
            queries.getSeriesMappingsByReadingListId(
                fixture.readingListId,
            ).awaitAsList() shouldBe emptyList()
        }
    }

    private suspend fun insertFixture(queries: Reading_listsQueries): Fixture {
        queries.insertReadingList(
            name = "Fixture",
            description = null,
            declaredIssueCount = 1,
            extraAttributes = "{}",
            extraElements = "{}",
            warnings = "[]",
            createdAt = 1,
            updatedAt = 1,
        )
        val readingListId = queries.lastInsertRowId().awaitAsOne()

        queries.insertReadingListEntry(
            readingListId = readingListId,
            position = 0,
            series = "Example",
            number = "1",
            volume = null,
            year = null,
            extraAttributes = "{}",
            extraElements = "{}",
            resolutionState = "UNSEARCHED",
        )
        val entryId = queries.lastInsertRowId().awaitAsOne()

        return Fixture(
            readingListId = readingListId,
            entryId = entryId,
        )
    }

    private suspend fun countRows(
        driver: JdbcSqliteDriver,
        tableName: String,
    ): Long {
        require(tableName in RESOLUTION_TABLES)
        return driver.executeQuery(
            null,
            "SELECT count(*) FROM $tableName",
            { cursor -> QueryResult.Value(cursor.getLong(0)!!) },
            0,
        ).await()
    }

    private suspend fun withQueries(
        block: suspend (JdbcSqliteDriver, Reading_listsQueries) -> Unit,
    ) {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            Database.Schema.create(driver).await()
            driver.execute(
                null,
                "PRAGMA foreign_keys = ON",
                0,
            ).await()
            block(driver, Reading_listsQueries(driver))
        } finally {
            driver.close()
        }
    }

    private data class Fixture(
        val readingListId: Long,
        val entryId: Long,
    )

    private companion object {
        val RESOLUTION_TABLES = setOf(
            "reading_list_match_candidates",
            "reading_list_candidate_rejections",
            "reading_list_entry_overrides",
            "reading_list_series_mappings",
        )
    }
}
