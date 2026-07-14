package tachiyomi.data.readinglist

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
import tachiyomi.domain.readinglist.model.ReadingListCandidateIdentity
import tachiyomi.domain.readinglist.model.ReadingListEntryOverrideUpdate
import tachiyomi.domain.readinglist.model.ReadingListMatchCandidateSnapshot
import tachiyomi.domain.readinglist.model.ReadingListProtectedWriteResult
import java.util.ArrayDeque

@Execution(ExecutionMode.CONCURRENT)
class ReadingListResolutionRepositoryImplTest {

    @Test
    fun `confirmed entries refuse candidate replacement`() = runTest {
        withRepository { repository, _, fixture ->
            val original = candidate(candidateId = "original")
            repository.replaceMatchCandidates(
                entryId = fixture.entryId,
                candidates = listOf(original),
            ) shouldBe ReadingListProtectedWriteResult.APPLIED
            repository.confirmResolution(
                entryId = fixture.entryId,
                candidate = original,
            ) shouldBe true

            repository.replaceMatchCandidates(
                entryId = fixture.entryId,
                candidates = listOf(candidate(candidateId = "replacement")),
            ) shouldBe ReadingListProtectedWriteResult.USER_CONFIRMED

            val stored = repository.get(fixture.readingListId)!!
            stored.candidates.map { candidate ->
                candidate.snapshot.identity.candidateId
            } shouldContainExactly listOf("original")
        }
    }

    @Test
    fun `candidate rejections survive candidate refresh`() = runTest {
        withRepository { repository, _, fixture ->
            val candidate = candidate(candidateId = "rejected")
            repository.replaceMatchCandidates(
                entryId = fixture.entryId,
                candidates = listOf(candidate),
            ) shouldBe ReadingListProtectedWriteResult.APPLIED
            repository.rejectCandidate(
                entryId = fixture.entryId,
                candidate = candidate,
            ) shouldBe true

            repository.replaceMatchCandidates(
                entryId = fixture.entryId,
                candidates = listOf(candidate.copy(sourceName = "Refreshed fixture")),
            ) shouldBe ReadingListProtectedWriteResult.APPLIED

            val stored = repository.get(fixture.readingListId)!!
            stored.rejections.map { rejection ->
                rejection.identity.candidateId
            } shouldContainExactly listOf("rejected")
            stored.candidates.single().rejected shouldBe true
            stored.candidates.single().snapshot.sourceName shouldBe "Refreshed fixture"
        }
    }

    @Test
    fun `entry override updates preserve creation time`() = runTest {
        val timestamps = ArrayDeque(listOf(10L, 20L))
        withRepository(
            currentTimeMillis = { timestamps.removeFirst() },
        ) { repository, _, fixture ->
            repository.setEntryOverride(
                entryId = fixture.entryId,
                override = ReadingListEntryOverrideUpdate(
                    sourceId = 7,
                    mangaUrl = null,
                    chapterUrl = null,
                ),
            ) shouldBe true
            repository.setEntryOverride(
                entryId = fixture.entryId,
                override = ReadingListEntryOverrideUpdate(
                    sourceId = 9,
                    mangaUrl = "/series",
                    chapterUrl = "/chapter",
                ),
            ) shouldBe true

            val override = repository.get(fixture.readingListId)!!
                .entryOverrides
                .single()
            override.sourceId shouldBe 9L
            override.mangaUrl shouldBe "/series"
            override.chapterUrl shouldBe "/chapter"
            override.createdAt shouldBe 10L
            override.updatedAt shouldBe 20L
        }
    }

    private suspend fun withRepository(
        currentTimeMillis: () -> Long = { 1L },
        block: suspend (
            ReadingListResolutionRepositoryImpl,
            Reading_listsQueries,
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
                currentTimeMillis = currentTimeMillis,
            )
            block(repository, queries, fixture)
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
            resolutionState = "UNSEARCHED",
        )
        val entryId = queries.lastInsertRowId().awaitAsOne()

        return Fixture(
            readingListId = readingListId,
            entryId = entryId,
        )
    }

    private fun candidate(
        candidateId: String,
        sourceId: Long = 7,
        sourceName: String = "Fixture",
    ): ReadingListMatchCandidateSnapshot {
        return ReadingListMatchCandidateSnapshot(
            identity = ReadingListCandidateIdentity(
                sourceId = sourceId,
                candidateId = candidateId,
            ),
            sourceName = sourceName,
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
                sourcePreference = SourcePreferenceLevel.NONE,
                sourcePreferencePoints = 0.0,
                confirmedHistory = ConfirmedHistoryEvidence.NONE,
                confirmedHistoryPoints = 0.0,
                total = 88.0,
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
