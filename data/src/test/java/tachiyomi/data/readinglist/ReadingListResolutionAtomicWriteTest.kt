package tachiyomi.data.readinglist

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import tachiyomi.data.Chapters
import tachiyomi.data.Database
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.History
import tachiyomi.data.Mangas
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.data.Reading_listsQueries
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.UpdateStrategyColumnAdapter
import tachiyomi.domain.readinglist.matching.ConfirmedHistoryEvidence
import tachiyomi.domain.readinglist.matching.EvidenceAgreement
import tachiyomi.domain.readinglist.matching.MatchDecisionReason
import tachiyomi.domain.readinglist.matching.MatchScoreBreakdown
import tachiyomi.domain.readinglist.matching.SourcePreferenceLevel
import tachiyomi.domain.readinglist.model.ReadingListAutomaticResolutionUpdate
import tachiyomi.domain.readinglist.model.ReadingListCandidateIdentity
import tachiyomi.domain.readinglist.model.ReadingListEntryResolutionState
import tachiyomi.domain.readinglist.model.ReadingListMatchCandidateSnapshot
import tachiyomi.domain.readinglist.model.ReadingListProtectedWriteResult

@Execution(ExecutionMode.CONCURRENT)
class ReadingListResolutionAtomicWriteTest {

    @Test
    fun `candidate replacement and automatic match are committed together`() = runTest {
        withRepository { repository, queries, _, fixture ->
            val candidate = candidate()

            repository.replaceMatchCandidatesAndApplyAutomaticResolution(
                entryId = fixture.entryId,
                candidates = listOf(candidate),
                update = ReadingListAutomaticResolutionUpdate(
                    state = ReadingListEntryResolutionState.AUTO_MATCHED,
                    leadingConfidence = candidate.score,
                    matcherVersion = candidate.matcherVersion,
                    acceptedCandidate = candidate,
                ),
            ) shouldBe ReadingListProtectedWriteResult.APPLIED

            val entry = queries.getReadingListEntries(fixture.readingListId)
                .awaitAsList()
                .single()
            entry.resolutionState shouldBe ReadingListEntryResolutionState.AUTO_MATCHED.name
            entry.matchedSourceId shouldBe candidate.identity.sourceId
            entry.matchedMangaUrl shouldBe candidate.mangaUrl
            entry.matchedChapterUrl shouldBe candidate.chapterUrl
            entry.confidence shouldBe candidate.score

            repository.get(fixture.readingListId)!!
                .candidates
                .map { stored -> stored.snapshot.identity.candidateId } shouldContainExactly
                listOf(candidate.identity.candidateId)
        }
    }

    @Test
    fun `confirmed entries refuse the combined automatic write`() = runTest {
        withRepository { repository, _, _, fixture ->
            val original = candidate(candidateId = "original")
            repository.confirmResolution(
                entryId = fixture.entryId,
                candidate = original,
            ) shouldBe true

            repository.replaceMatchCandidatesAndApplyAutomaticResolution(
                entryId = fixture.entryId,
                candidates = listOf(candidate(candidateId = "replacement")),
                update = ReadingListAutomaticResolutionUpdate(
                    state = ReadingListEntryResolutionState.UNRESOLVED,
                    leadingConfidence = null,
                    matcherVersion = 1,
                    acceptedCandidate = null,
                ),
            ) shouldBe ReadingListProtectedWriteResult.USER_CONFIRMED

            repository.get(fixture.readingListId)!!
                .candidates shouldBe emptyList()
        }
    }

    @Test
    fun `skipped entries refuse the combined automatic write`() = runTest {
        withRepository { repository, queries, driver, fixture ->
            driver.execute(
                null,
                "UPDATE reading_list_entries SET skipped = 1 WHERE _id = ?",
                1,
            ) {
                bindLong(0, fixture.entryId)
            }.await()

            repository.replaceMatchCandidatesAndApplyAutomaticResolution(
                entryId = fixture.entryId,
                candidates = listOf(candidate()),
                update = ReadingListAutomaticResolutionUpdate(
                    state = ReadingListEntryResolutionState.UNRESOLVED,
                    leadingConfidence = null,
                    matcherVersion = 1,
                    acceptedCandidate = null,
                ),
            ) shouldBe ReadingListProtectedWriteResult.SKIPPED

            queries.getReadingListEntries(fixture.readingListId)
                .awaitAsList()
                .single()
                .let { entry ->
                    entry.resolutionState shouldBe ReadingListEntryResolutionState.UNSEARCHED.name
                    entry.skipped shouldBe true
                }
            repository.get(fixture.readingListId)!!
                .candidates shouldBe emptyList()
        }
    }

    @Test
    fun `combined write requires the accepted snapshot to match exactly`() = runTest {
        withRepository { repository, _, _, fixture ->
            val storedCandidate = candidate()
            val mismatchedAcceptedCandidate = storedCandidate.copy(
                mangaUrl = "/series/different",
            )
            var failure: IllegalArgumentException? = null

            try {
                repository.replaceMatchCandidatesAndApplyAutomaticResolution(
                    entryId = fixture.entryId,
                    candidates = listOf(storedCandidate),
                    update = ReadingListAutomaticResolutionUpdate(
                        state = ReadingListEntryResolutionState.AUTO_MATCHED,
                        leadingConfidence = mismatchedAcceptedCandidate.score,
                        matcherVersion = mismatchedAcceptedCandidate.matcherVersion,
                        acceptedCandidate = mismatchedAcceptedCandidate,
                    ),
                )
            } catch (error: IllegalArgumentException) {
                failure = error
            }

            failure?.message shouldBe
                "The accepted candidate must exactly match a candidate in the replacement set"
        }
    }

    private suspend fun withRepository(
        block: suspend (
            ReadingListResolutionRepositoryImpl,
            Reading_listsQueries,
            JdbcSqliteDriver,
            Fixture,
        ) -> Unit,
    ) {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            Database.Schema.create(driver).await()
            driver.execute(
                null,
                "PRAGMA foreign_keys = ON",
                0,
            ).await()
            val database = Database(
                driver = driver,
                historyAdapter = History.Adapter(
                    last_readAdapter = DateColumnAdapter,
                ),
                mangasAdapter = Mangas.Adapter(
                    genreAdapter = StringListColumnAdapter,
                    update_strategyAdapter = UpdateStrategyColumnAdapter,
                    memoAdapter = MemoColumnAdapter,
                ),
                chaptersAdapter = Chapters.Adapter(
                    memoAdapter = MemoColumnAdapter,
                ),
            )
            val queries = database.reading_listsQueries
            val fixture = insertFixture(queries)
            val repository = ReadingListResolutionRepositoryImpl(
                database = database,
                json = Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                },
                currentTimeMillis = { 2L },
            )
            block(repository, queries, driver, fixture)
        } finally {
            driver.close()
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
            resolutionState = ReadingListEntryResolutionState.UNSEARCHED.name,
        )
        val entryId = queries.lastInsertRowId().awaitAsOne()

        return Fixture(
            readingListId = readingListId,
            entryId = entryId,
        )
    }

    private fun candidate(
        candidateId: String = "candidate",
    ): ReadingListMatchCandidateSnapshot {
        return ReadingListMatchCandidateSnapshot(
            identity = ReadingListCandidateIdentity(
                sourceId = 7,
                candidateId = candidateId,
            ),
            sourceName = "Fixture",
            sourceLanguage = "en",
            mangaUrl = "/series/$candidateId",
            chapterUrl = "/chapter/$candidateId",
            seriesTitle = "Example",
            issueNumber = "1",
            volume = null,
            year = null,
            breakdown = MatchScoreBreakdown(
                titleSimilarity = 1.0,
                titlePoints = 58.0,
                issueEquivalent = true,
                issuePoints = 30.0,
                yearEvidence = EvidenceAgreement.UNKNOWN,
                yearPoints = 0.0,
                volumeEvidence = EvidenceAgreement.UNKNOWN,
                volumePoints = 0.0,
                externalIdentifierEvidence = EvidenceAgreement.UNKNOWN,
                externalIdentifierPoints = 0.0,
                sourcePreference = SourcePreferenceLevel.READING_LIST,
                sourcePreferencePoints = 1.0,
                confirmedHistory = ConfirmedHistoryEvidence.NONE,
                confirmedHistoryPoints = 0.0,
                total = 89.0,
            ),
            decisionReason = MatchDecisionReason.AUTO_ACCEPTED,
            leadOverRunnerUp = null,
            matcherVersion = 1,
        )
    }

    private data class Fixture(
        val readingListId: Long,
        val entryId: Long,
    )
}
