package tachiyomi.domain.readinglist.repository

import tachiyomi.domain.readinglist.model.ReadingListAutomaticResolutionUpdate
import tachiyomi.domain.readinglist.model.ReadingListCandidateIdentity
import tachiyomi.domain.readinglist.model.ReadingListEntryOverrideUpdate
import tachiyomi.domain.readinglist.model.ReadingListMatchCandidateSnapshot
import tachiyomi.domain.readinglist.model.ReadingListProtectedWriteResult
import tachiyomi.domain.readinglist.model.ReadingListResolutionData
import tachiyomi.domain.readinglist.model.ReadingListSeriesMappingUpdate
import tachiyomi.domain.readinglist.model.ReadingListSeriesMappingWriteResult

interface ReadingListResolutionRepository {

    suspend fun get(readingListId: Long): ReadingListResolutionData?

    suspend fun replaceMatchCandidates(
        entryId: Long,
        candidates: List<ReadingListMatchCandidateSnapshot>,
    ): ReadingListProtectedWriteResult

    suspend fun applyAutomaticResolution(
        entryId: Long,
        update: ReadingListAutomaticResolutionUpdate,
    ): ReadingListProtectedWriteResult

    suspend fun confirmResolution(
        entryId: Long,
        candidate: ReadingListMatchCandidateSnapshot,
    ): Boolean

    suspend fun rejectCandidate(
        entryId: Long,
        candidate: ReadingListMatchCandidateSnapshot,
    ): Boolean

    suspend fun clearCandidateRejection(
        entryId: Long,
        identity: ReadingListCandidateIdentity,
    ): Boolean

    suspend fun setEntryOverride(
        entryId: Long,
        override: ReadingListEntryOverrideUpdate,
    ): Boolean

    suspend fun clearEntryOverride(entryId: Long): Boolean

    suspend fun applyAutomaticSeriesMapping(
        readingListId: Long,
        mapping: ReadingListSeriesMappingUpdate,
    ): ReadingListSeriesMappingWriteResult

    suspend fun confirmSeriesMapping(
        readingListId: Long,
        mapping: ReadingListSeriesMappingUpdate,
    ): Boolean

    suspend fun clearSeriesMapping(
        readingListId: Long,
        seriesKey: String,
    ): Boolean
}
