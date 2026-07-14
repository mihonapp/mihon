package tachiyomi.data.readinglist

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlinx.serialization.json.Json
import tachiyomi.data.Database
import tachiyomi.data.Reading_listsQueries
import tachiyomi.domain.readinglist.matching.MatchDecisionReason
import tachiyomi.domain.readinglist.model.ReadingListAutomaticResolutionUpdate
import tachiyomi.domain.readinglist.model.ReadingListCandidateIdentity
import tachiyomi.domain.readinglist.model.ReadingListCandidateRejection
import tachiyomi.domain.readinglist.model.ReadingListEntryOverride
import tachiyomi.domain.readinglist.model.ReadingListEntryOverrideUpdate
import tachiyomi.domain.readinglist.model.ReadingListMatchCandidateSnapshot
import tachiyomi.domain.readinglist.model.ReadingListProtectedWriteResult
import tachiyomi.domain.readinglist.model.ReadingListResolutionData
import tachiyomi.domain.readinglist.model.ReadingListSeriesMapping
import tachiyomi.domain.readinglist.model.ReadingListSeriesMappingUpdate
import tachiyomi.domain.readinglist.model.ReadingListSeriesMappingWriteResult
import tachiyomi.domain.readinglist.model.ReadingListStoredMatchCandidate
import tachiyomi.domain.readinglist.repository.ReadingListResolutionRepository
import kotlin.math.abs

class ReadingListResolutionRepositoryImpl(
    private val database: Database,
    json: Json,
    private val currentTimeMillis: () -> Long = { System.currentTimeMillis() },
) : ReadingListResolutionRepository {

    private val codec = ReadingListStorageCodec(json)

    override suspend fun get(readingListId: Long): ReadingListResolutionData? {
        return database.transactionWithResult {
            val queries = database.reading_listsQueries
            if (!queries.readingListExists(readingListId).awaitAsOne()) {
                return@transactionWithResult null
            }

            val rejections = queries.getCandidateRejectionsByReadingListId(readingListId)
                .awaitAsList()
                .map { row ->
                    ReadingListCandidateRejection(
                        entryId = row.entryId,
                        identity = ReadingListCandidateIdentity(
                            sourceId = row.sourceId,
                            candidateId = row.candidateId,
                        ),
                        mangaUrl = row.mangaUrl,
                        chapterUrl = row.chapterUrl,
                        rejectedAt = row.rejectedAt,
                    )
                }
            val rejectedIdentities = rejections
                .map { rejection -> rejection.entryId to rejection.identity }
                .toSet()

            val candidates = queries.getMatchCandidatesByReadingListId(readingListId)
                .awaitAsList()
                .map { row ->
                    val breakdown = codec.decodeMatchScoreBreakdown(row.scoreBreakdown)
                    check(abs(row.score - breakdown.total) <= SCORE_STORAGE_EPSILON) {
                        "Stored candidate score does not match its breakdown"
                    }
                    check(
                        abs(row.titleSimilarity - breakdown.titleSimilarity) <=
                            SCORE_STORAGE_EPSILON,
                    ) {
                        "Stored title similarity does not match its breakdown"
                    }
                    check(row.issueEquivalent == breakdown.issueEquivalent) {
                        "Stored issue equivalence does not match its breakdown"
                    }

                    val identity = ReadingListCandidateIdentity(
                        sourceId = row.sourceId,
                        candidateId = row.candidateId,
                    )
                    ReadingListStoredMatchCandidate(
                        entryId = row.entryId,
                        snapshot = ReadingListMatchCandidateSnapshot(
                            identity = identity,
                            sourceName = row.sourceName,
                            sourceLanguage = row.sourceLanguage,
                            mangaUrl = row.mangaUrl,
                            chapterUrl = row.chapterUrl,
                            seriesTitle = row.seriesTitle,
                            issueNumber = row.issueNumber,
                            volume = row.volume?.toExactInt("candidate volume"),
                            year = row.year?.toExactInt("candidate year"),
                            breakdown = breakdown,
                            decisionReason = MatchDecisionReason.valueOf(row.decisionReason),
                            leadOverRunnerUp = row.leadOverRunnerUp,
                            matcherVersion = row.matcherVersion,
                        ),
                        rejected = (row.entryId to identity) in rejectedIdentities,
                        createdAt = row.createdAt,
                        updatedAt = row.updatedAt,
                    )
                }

            val entryOverrides = queries.getEntryOverridesByReadingListId(readingListId)
                .awaitAsList()
                .map { row ->
                    ReadingListEntryOverride(
                        entryId = row.entryId,
                        sourceId = row.sourceId,
                        mangaUrl = row.mangaUrl,
                        chapterUrl = row.chapterUrl,
                        createdAt = row.createdAt,
                        updatedAt = row.updatedAt,
                    )
                }

            val seriesMappings = queries.getSeriesMappingsByReadingListId(readingListId)
                .awaitAsList()
                .map { row ->
                    ReadingListSeriesMapping(
                        readingListId = row.readingListId,
                        seriesKey = row.seriesKey,
                        seriesTitle = row.seriesTitle,
                        sourceId = row.sourceId,
                        mangaUrl = row.mangaUrl,
                        userConfirmed = row.userConfirmed,
                        createdAt = row.createdAt,
                        updatedAt = row.updatedAt,
                    )
                }

            ReadingListResolutionData(
                readingListId = readingListId,
                candidates = candidates,
                rejections = rejections,
                entryOverrides = entryOverrides,
                seriesMappings = seriesMappings,
            )
        }
    }

    override suspend fun replaceMatchCandidates(
        entryId: Long,
        candidates: List<ReadingListMatchCandidateSnapshot>,
    ): ReadingListProtectedWriteResult {
        requireValidCandidateReplacement(candidates)

        return database.transactionWithResult {
            val queries = database.reading_listsQueries
            val guard = queries.getEntryResolutionGuard(entryId).awaitAsOneOrNull()
                ?: return@transactionWithResult ReadingListProtectedWriteResult.ENTRY_NOT_FOUND
            if (guard.userConfirmed) {
                return@transactionWithResult ReadingListProtectedWriteResult.USER_CONFIRMED
            }
            if (guard.skipped) {
                return@transactionWithResult ReadingListProtectedWriteResult.SKIPPED
            }

            val timestamp = currentTimeMillis()
            replaceCandidates(
                queries = queries,
                entryId = entryId,
                candidates = candidates,
                timestamp = timestamp,
            )
            queries.touchReadingList(
                updatedAt = timestamp,
                id = guard.readingListId,
            )
            ReadingListProtectedWriteResult.APPLIED
        }
    }

    override suspend fun replaceMatchCandidatesAndApplyAutomaticResolution(
        entryId: Long,
        candidates: List<ReadingListMatchCandidateSnapshot>,
        update: ReadingListAutomaticResolutionUpdate,
    ): ReadingListProtectedWriteResult {
        requireValidCandidateReplacement(candidates)
        require(candidates.all { candidate -> candidate.matcherVersion == update.matcherVersion }) {
            "Candidate replacement and automatic resolution must use one matcher version"
        }
        update.acceptedCandidate?.let { acceptedCandidate ->
            require(candidates.any { candidate -> candidate == acceptedCandidate }) {
                "The accepted candidate must exactly match a candidate in the replacement set"
            }
        }

        return database.transactionWithResult {
            val queries = database.reading_listsQueries
            val guard = queries.getEntryResolutionGuard(entryId).awaitAsOneOrNull()
                ?: return@transactionWithResult ReadingListProtectedWriteResult.ENTRY_NOT_FOUND
            if (guard.userConfirmed) {
                return@transactionWithResult ReadingListProtectedWriteResult.USER_CONFIRMED
            }
            if (guard.skipped) {
                return@transactionWithResult ReadingListProtectedWriteResult.SKIPPED
            }

            val timestamp = currentTimeMillis()
            replaceCandidates(
                queries = queries,
                entryId = entryId,
                candidates = candidates,
                timestamp = timestamp,
            )
            applyAutomaticResolution(
                queries = queries,
                entryId = entryId,
                update = update,
            )
            queries.touchReadingList(
                updatedAt = timestamp,
                id = guard.readingListId,
            )
            ReadingListProtectedWriteResult.APPLIED
        }
    }

    override suspend fun applyAutomaticResolution(
        entryId: Long,
        update: ReadingListAutomaticResolutionUpdate,
    ): ReadingListProtectedWriteResult {
        return database.transactionWithResult {
            val queries = database.reading_listsQueries
            val guard = queries.getEntryResolutionGuard(entryId).awaitAsOneOrNull()
                ?: return@transactionWithResult ReadingListProtectedWriteResult.ENTRY_NOT_FOUND
            if (guard.userConfirmed) {
                return@transactionWithResult ReadingListProtectedWriteResult.USER_CONFIRMED
            }
            if (guard.skipped) {
                return@transactionWithResult ReadingListProtectedWriteResult.SKIPPED
            }

            val acceptedCandidate = update.acceptedCandidate
            val updatedRows = queries.applyAutomaticResolution(
                resolutionState = update.state.name,
                matchedSourceId = acceptedCandidate?.identity?.sourceId,
                matchedMangaUrl = acceptedCandidate?.mangaUrl,
                matchedChapterUrl = acceptedCandidate?.chapterUrl,
                confidence = update.leadingConfidence,
                matcherVersion = update.matcherVersion,
                entryId = entryId,
            )
            if (updatedRows != 1L) {
                val latestGuard = queries.getEntryResolutionGuard(entryId).awaitAsOneOrNull()
                    ?: return@transactionWithResult ReadingListProtectedWriteResult.ENTRY_NOT_FOUND
                return@transactionWithResult when {
                    latestGuard.userConfirmed -> ReadingListProtectedWriteResult.USER_CONFIRMED
                    latestGuard.skipped -> ReadingListProtectedWriteResult.SKIPPED
                    else -> error("Automatic reading-list resolution did not update its entry")
                }
            }

            queries.touchReadingList(
                updatedAt = currentTimeMillis(),
                id = guard.readingListId,
            )
            ReadingListProtectedWriteResult.APPLIED
        }
    }

    override suspend fun confirmResolution(
        entryId: Long,
        candidate: ReadingListMatchCandidateSnapshot,
    ): Boolean {
        return database.transactionWithResult {
            val queries = database.reading_listsQueries
            val guard = queries.getEntryResolutionGuard(entryId).awaitAsOneOrNull()
                ?: return@transactionWithResult false

            val updatedRows = queries.confirmResolution(
                matchedSourceId = candidate.identity.sourceId,
                matchedMangaUrl = candidate.mangaUrl,
                matchedChapterUrl = candidate.chapterUrl,
                confidence = candidate.score,
                matcherVersion = candidate.matcherVersion,
                entryId = entryId,
            )
            check(updatedRows == 1L) {
                "Explicit reading-list confirmation did not update its entry"
            }
            queries.deleteCandidateRejection(
                entryId = entryId,
                sourceId = candidate.identity.sourceId,
                candidateId = candidate.identity.candidateId,
            )
            queries.touchReadingList(
                updatedAt = currentTimeMillis(),
                id = guard.readingListId,
            )
            true
        }
    }

    override suspend fun rejectCandidate(
        entryId: Long,
        candidate: ReadingListMatchCandidateSnapshot,
    ): Boolean {
        return database.transactionWithResult {
            val queries = database.reading_listsQueries
            val guard = queries.getEntryResolutionGuard(entryId).awaitAsOneOrNull()
                ?: return@transactionWithResult false
            val timestamp = currentTimeMillis()

            queries.insertCandidateRejection(
                entryId = entryId,
                sourceId = candidate.identity.sourceId,
                candidateId = candidate.identity.candidateId,
                mangaUrl = candidate.mangaUrl,
                chapterUrl = candidate.chapterUrl,
                rejectedAt = timestamp,
            )
            queries.touchReadingList(
                updatedAt = timestamp,
                id = guard.readingListId,
            )
            true
        }
    }

    override suspend fun clearCandidateRejection(
        entryId: Long,
        identity: ReadingListCandidateIdentity,
    ): Boolean {
        return database.transactionWithResult {
            val queries = database.reading_listsQueries
            val deletedRows = queries.deleteCandidateRejection(
                entryId = entryId,
                sourceId = identity.sourceId,
                candidateId = identity.candidateId,
            )
            if (deletedRows == 0L) {
                return@transactionWithResult false
            }
            queries.touchReadingListForEntry(
                updatedAt = currentTimeMillis(),
                entryId = entryId,
            )
            true
        }
    }

    override suspend fun setEntryOverride(
        entryId: Long,
        override: ReadingListEntryOverrideUpdate,
    ): Boolean {
        return database.transactionWithResult {
            val queries = database.reading_listsQueries
            val guard = queries.getEntryResolutionGuard(entryId).awaitAsOneOrNull()
                ?: return@transactionWithResult false
            val timestamp = currentTimeMillis()

            queries.upsertEntryOverride(
                entryId = entryId,
                sourceId = override.sourceId,
                mangaUrl = override.mangaUrl,
                chapterUrl = override.chapterUrl,
                createdAt = timestamp,
                updatedAt = timestamp,
            )
            queries.touchReadingList(
                updatedAt = timestamp,
                id = guard.readingListId,
            )
            true
        }
    }

    override suspend fun clearEntryOverride(entryId: Long): Boolean {
        return database.transactionWithResult {
            val queries = database.reading_listsQueries
            val deletedRows = queries.deleteEntryOverride(entryId)
            if (deletedRows == 0L) {
                return@transactionWithResult false
            }
            queries.touchReadingListForEntry(
                updatedAt = currentTimeMillis(),
                entryId = entryId,
            )
            true
        }
    }

    override suspend fun applyAutomaticSeriesMapping(
        readingListId: Long,
        mapping: ReadingListSeriesMappingUpdate,
    ): ReadingListSeriesMappingWriteResult {
        return database.transactionWithResult {
            val queries = database.reading_listsQueries
            if (!queries.readingListExists(readingListId).awaitAsOne()) {
                return@transactionWithResult ReadingListSeriesMappingWriteResult.READING_LIST_NOT_FOUND
            }

            val timestamp = currentTimeMillis()
            val updatedRows = queries.applyAutomaticSeriesMapping(
                readingListId = readingListId,
                seriesKey = mapping.seriesKey,
                seriesTitle = mapping.seriesTitle,
                sourceId = mapping.sourceId,
                mangaUrl = mapping.mangaUrl,
                createdAt = timestamp,
                updatedAt = timestamp,
            )
            if (updatedRows == 0L) {
                return@transactionWithResult ReadingListSeriesMappingWriteResult.USER_CONFIRMED
            }

            queries.touchReadingList(
                updatedAt = timestamp,
                id = readingListId,
            )
            ReadingListSeriesMappingWriteResult.APPLIED
        }
    }

    override suspend fun confirmSeriesMapping(
        readingListId: Long,
        mapping: ReadingListSeriesMappingUpdate,
    ): Boolean {
        return database.transactionWithResult {
            val queries = database.reading_listsQueries
            if (!queries.readingListExists(readingListId).awaitAsOne()) {
                return@transactionWithResult false
            }

            val timestamp = currentTimeMillis()
            queries.confirmSeriesMapping(
                readingListId = readingListId,
                seriesKey = mapping.seriesKey,
                seriesTitle = mapping.seriesTitle,
                sourceId = mapping.sourceId,
                mangaUrl = mapping.mangaUrl,
                createdAt = timestamp,
                updatedAt = timestamp,
            )
            queries.touchReadingList(
                updatedAt = timestamp,
                id = readingListId,
            )
            true
        }
    }

    override suspend fun clearSeriesMapping(
        readingListId: Long,
        seriesKey: String,
    ): Boolean {
        require(seriesKey.isNotBlank()) {
            "Series mapping key cannot be blank"
        }

        return database.transactionWithResult {
            val queries = database.reading_listsQueries
            val deletedRows = queries.deleteSeriesMapping(
                readingListId = readingListId,
                seriesKey = seriesKey,
            )
            if (deletedRows == 0L) {
                return@transactionWithResult false
            }
            queries.touchReadingList(
                updatedAt = currentTimeMillis(),
                id = readingListId,
            )
            true
        }
    }

    private fun requireValidCandidateReplacement(
        candidates: List<ReadingListMatchCandidateSnapshot>,
    ) {
        require(
            candidates.map(ReadingListMatchCandidateSnapshot::identity)
                .distinct()
                .size == candidates.size,
        ) {
            "Reading-list candidate replacement contains duplicate identities"
        }
        candidates.firstOrNull()?.let { firstCandidate ->
            require(
                candidates.all { candidate ->
                    candidate.matcherVersion == firstCandidate.matcherVersion
                },
            ) {
                "Candidate replacement must use one matcher version"
            }
            require(
                candidates.all { candidate ->
                    candidate.decisionReason == firstCandidate.decisionReason
                },
            ) {
                "Candidate replacement must use one decision reason"
            }
            require(
                candidates.all { candidate ->
                    candidate.leadOverRunnerUp == firstCandidate.leadOverRunnerUp
                },
            ) {
                "Candidate replacement must use one runner-up lead"
            }
        }
    }

    private suspend fun replaceCandidates(
        queries: Reading_listsQueries,
        entryId: Long,
        candidates: List<ReadingListMatchCandidateSnapshot>,
        timestamp: Long,
    ) {
        queries.deleteMatchCandidatesByEntryId(entryId)
        candidates.forEach { candidate ->
            queries.insertMatchCandidate(
                entryId = entryId,
                sourceId = candidate.identity.sourceId,
                candidateId = candidate.identity.candidateId,
                sourceName = candidate.sourceName,
                sourceLanguage = candidate.sourceLanguage,
                mangaUrl = candidate.mangaUrl,
                chapterUrl = candidate.chapterUrl,
                seriesTitle = candidate.seriesTitle,
                issueNumber = candidate.issueNumber,
                volume = candidate.volume?.toLong(),
                year = candidate.year?.toLong(),
                score = candidate.score,
                titleSimilarity = candidate.breakdown.titleSimilarity,
                issueEquivalent = candidate.breakdown.issueEquivalent,
                scoreBreakdown = codec.encodeMatchScoreBreakdown(candidate.breakdown),
                decisionReason = candidate.decisionReason.name,
                leadOverRunnerUp = candidate.leadOverRunnerUp,
                matcherVersion = candidate.matcherVersion,
                createdAt = timestamp,
                updatedAt = timestamp,
            )
        }
    }

    private suspend fun applyAutomaticResolution(
        queries: Reading_listsQueries,
        entryId: Long,
        update: ReadingListAutomaticResolutionUpdate,
    ) {
        val acceptedCandidate = update.acceptedCandidate
        val updatedRows = queries.applyAutomaticResolution(
            resolutionState = update.state.name,
            matchedSourceId = acceptedCandidate?.identity?.sourceId,
            matchedMangaUrl = acceptedCandidate?.mangaUrl,
            matchedChapterUrl = acceptedCandidate?.chapterUrl,
            confidence = update.leadingConfidence,
            matcherVersion = update.matcherVersion,
            entryId = entryId,
        )
        check(updatedRows == 1L) {
            "Automatic reading-list resolution did not update its unconfirmed entry"
        }
    }

    private fun Long.toExactInt(fieldName: String): Int {
        check(this in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
            "Stored $fieldName is outside the supported Int range"
        }
        return toInt()
    }

    private companion object {
        const val SCORE_STORAGE_EPSILON = 0.0001
    }
}
