package eu.kanade.tachiyomi.ui.readinglist

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.readinglist.matching.ReadingListSeriesKey
import tachiyomi.domain.readinglist.model.ReadingList
import tachiyomi.domain.readinglist.model.ReadingListCandidateIdentity
import tachiyomi.domain.readinglist.model.ReadingListCandidateRejection
import tachiyomi.domain.readinglist.model.ReadingListEntry
import tachiyomi.domain.readinglist.model.ReadingListEntryOverride
import tachiyomi.domain.readinglist.model.ReadingListEntryResolutionState
import tachiyomi.domain.readinglist.model.ReadingListSeriesMapping
import tachiyomi.domain.readinglist.model.ReadingListSeriesMappingUpdate
import tachiyomi.domain.readinglist.model.ReadingListStoredMatchCandidate
import tachiyomi.domain.readinglist.repository.ReadingListRepository
import tachiyomi.domain.readinglist.repository.ReadingListResolutionRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ReadingListReviewScreenModel(
    private val readingListId: Long,
    private val readingListRepository: ReadingListRepository = Injekt.get(),
    private val resolutionRepository: ReadingListResolutionRepository = Injekt.get(),
) : StateScreenModel<ReadingListReviewScreenState>(ReadingListReviewScreenState()) {

    private val operations = ReadingListReviewOperations(resolutionRepository)
    private val _events = Channel<ReadingListReviewEvent>()
    val events = _events.receiveAsFlow()

    init {
        reload(showLoading = true)
    }

    fun reload(showLoading: Boolean = false) {
        if (showLoading) {
            mutableState.update { current -> current.copy(isLoading = true) }
        }
        screenModelScope.launch {
            try {
                val review = withIOContext { loadReviewData() }
                mutableState.update { current ->
                    current.copy(
                        isLoading = false,
                        review = review,
                        isMissing = review == null,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.update { current -> current.copy(isLoading = false) }
                _events.send(ReadingListReviewEvent.ActionFailed)
            }
        }
    }

    fun confirmCandidate(entryId: Long, identity: ReadingListCandidateIdentity) {
        runAction(
            action = ReadingListReviewAction.ConfirmCandidate(entryId, identity),
            successEvent = ReadingListReviewEvent.CandidateConfirmed,
        ) { review ->
            operations.confirmCandidate(review, entryId, identity)
        }
    }

    fun rejectCandidate(entryId: Long, identity: ReadingListCandidateIdentity) {
        runAction(
            action = ReadingListReviewAction.RejectCandidate(entryId, identity),
            successEvent = ReadingListReviewEvent.CandidateRejected,
        ) { review ->
            operations.rejectCandidate(review, entryId, identity)
        }
    }

    fun restoreCandidate(entryId: Long, identity: ReadingListCandidateIdentity) {
        runAction(
            action = ReadingListReviewAction.RestoreCandidate(entryId, identity),
            successEvent = ReadingListReviewEvent.CandidateRestored,
        ) { review ->
            operations.restoreCandidate(review, entryId, identity)
        }
    }

    fun confirmSeriesMapping(entryId: Long, identity: ReadingListCandidateIdentity) {
        runAction(
            action = ReadingListReviewAction.ConfirmSeriesMapping(entryId, identity),
            successEvent = ReadingListReviewEvent.SeriesMappingConfirmed,
        ) { review ->
            operations.confirmSeriesMapping(review, entryId, identity)
        }
    }

    fun clearSeriesMapping(entryId: Long) {
        runAction(
            action = ReadingListReviewAction.ClearSeriesMapping(entryId),
            successEvent = ReadingListReviewEvent.SeriesMappingCleared,
        ) { review ->
            operations.clearSeriesMapping(review, entryId)
        }
    }

    private fun runAction(
        action: ReadingListReviewAction,
        successEvent: ReadingListReviewEvent,
        block: suspend (ReadingListReviewData) -> Boolean,
    ) {
        val review = state.value.review ?: return
        if (state.value.activeAction != null) return

        mutableState.update { current -> current.copy(activeAction = action) }
        screenModelScope.launch {
            try {
                val applied = withIOContext { block(review) }
                if (!applied) {
                    _events.send(ReadingListReviewEvent.ActionFailed)
                    return@launch
                }

                val refreshed = withIOContext { loadReviewData() }
                mutableState.update { current ->
                    current.copy(
                        review = refreshed,
                        isMissing = refreshed == null,
                    )
                }
                _events.send(
                    if (refreshed == null) {
                        ReadingListReviewEvent.ReadingListMissing
                    } else {
                        successEvent
                    },
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _events.send(ReadingListReviewEvent.ActionFailed)
            } finally {
                mutableState.update { current -> current.copy(activeAction = null) }
            }
        }
    }

    private suspend fun loadReviewData(): ReadingListReviewData? {
        val readingList = readingListRepository.get(readingListId) ?: return null
        val resolution = resolutionRepository.get(readingListId) ?: return null
        return buildReadingListReviewData(readingList, resolution)
    }
}

internal class ReadingListReviewOperations(
    private val repository: ReadingListResolutionRepository,
) {

    suspend fun confirmCandidate(
        review: ReadingListReviewData,
        entryId: Long,
        identity: ReadingListCandidateIdentity,
    ): Boolean {
        val candidate = review.findCandidate(entryId, identity) ?: return false
        return repository.confirmResolution(entryId, candidate.snapshot)
    }

    suspend fun rejectCandidate(
        review: ReadingListReviewData,
        entryId: Long,
        identity: ReadingListCandidateIdentity,
    ): Boolean {
        val candidate = review.findCandidate(entryId, identity) ?: return false
        return repository.rejectCandidate(entryId, candidate.snapshot)
    }

    suspend fun restoreCandidate(
        review: ReadingListReviewData,
        entryId: Long,
        identity: ReadingListCandidateIdentity,
    ): Boolean {
        val entry = review.entries.firstOrNull { item -> item.entry.id == entryId } ?: return false
        val knownIdentity = entry.candidates.any { candidate -> candidate.snapshot.identity == identity } ||
            entry.orphanRejections.any { rejection -> rejection.identity == identity }
        if (!knownIdentity) return false
        return repository.clearCandidateRejection(entryId, identity)
    }

    suspend fun confirmSeriesMapping(
        review: ReadingListReviewData,
        entryId: Long,
        identity: ReadingListCandidateIdentity,
    ): Boolean {
        val entry = review.entries.firstOrNull { item -> item.entry.id == entryId } ?: return false
        val candidate = entry.candidates.firstOrNull { item -> item.snapshot.identity == identity } ?: return false
        return repository.confirmSeriesMapping(
            readingListId = review.readingList.id,
            mapping = ReadingListSeriesMappingUpdate(
                seriesKey = entry.seriesKey,
                seriesTitle = entry.entry.series,
                sourceId = candidate.snapshot.identity.sourceId,
                mangaUrl = candidate.snapshot.mangaUrl,
            ),
        )
    }

    suspend fun clearSeriesMapping(
        review: ReadingListReviewData,
        entryId: Long,
    ): Boolean {
        val entry = review.entries.firstOrNull { item -> item.entry.id == entryId } ?: return false
        return repository.clearSeriesMapping(
            readingListId = review.readingList.id,
            seriesKey = entry.seriesKey,
        )
    }
}

internal fun buildReadingListReviewData(
    readingList: ReadingList,
    resolution: tachiyomi.domain.readinglist.model.ReadingListResolutionData,
): ReadingListReviewData {
    require(readingList.id == resolution.readingListId) {
        "Reading-list review data must belong to the same reading list"
    }

    val candidatesByEntry = resolution.candidates.groupBy(ReadingListStoredMatchCandidate::entryId)
    val rejectionsByEntry = resolution.rejections.groupBy(ReadingListCandidateRejection::entryId)
    val overridesByEntry = resolution.entryOverrides.associateBy(ReadingListEntryOverride::entryId)
    val mappingsByKey = resolution.seriesMappings.associateBy(ReadingListSeriesMapping::seriesKey)

    val entries = readingList.entries
        .sortedBy(ReadingListEntry::position)
        .map { entry ->
            val seriesKey = ReadingListSeriesKey.from(
                seriesTitle = entry.series,
                volume = entry.volume,
                year = entry.year,
            )
            val candidates = candidatesByEntry[entry.id].orEmpty()
            val candidateIdentities = candidates
                .mapTo(mutableSetOf()) { candidate -> candidate.snapshot.identity }
            ReadingListReviewEntry(
                entry = entry,
                seriesKey = seriesKey,
                candidates = candidates,
                orphanRejections = rejectionsByEntry[entry.id]
                    .orEmpty()
                    .filterNot { rejection -> rejection.identity in candidateIdentities },
                entryOverride = overridesByEntry[entry.id],
                seriesMapping = mappingsByKey[seriesKey],
            )
        }

    return ReadingListReviewData(
        readingList = readingList,
        entries = entries,
    )
}

private fun ReadingListReviewData.findCandidate(
    entryId: Long,
    identity: ReadingListCandidateIdentity,
): ReadingListStoredMatchCandidate? {
    return entries
        .firstOrNull { item -> item.entry.id == entryId }
        ?.candidates
        ?.firstOrNull { candidate -> candidate.snapshot.identity == identity }
}

@Immutable
data class ReadingListReviewScreenState(
    val isLoading: Boolean = true,
    val review: ReadingListReviewData? = null,
    val isMissing: Boolean = false,
    val activeAction: ReadingListReviewAction? = null,
)

@Immutable
data class ReadingListReviewData(
    val readingList: ReadingList,
    val entries: List<ReadingListReviewEntry>,
) {
    val needsReviewCount: Int
        get() = entries.count { item -> item.entry.needsManualAttention }

    val completedCount: Int
        get() = entries.count { item ->
            item.entry.resolutionState == ReadingListEntryResolutionState.AUTO_MATCHED ||
                item.entry.resolutionState == ReadingListEntryResolutionState.USER_CONFIRMED
        }

    val protectedCount: Int
        get() = entries.count { item -> item.entry.userConfirmed || item.entry.skipped }
}

@Immutable
data class ReadingListReviewEntry(
    val entry: ReadingListEntry,
    val seriesKey: String,
    val candidates: List<ReadingListStoredMatchCandidate>,
    val orphanRejections: List<ReadingListCandidateRejection>,
    val entryOverride: ReadingListEntryOverride?,
    val seriesMapping: ReadingListSeriesMapping?,
)

val ReadingListEntry.needsManualAttention: Boolean
    get() {
        if (userConfirmed || skipped) return false
        return when (resolutionState) {
            ReadingListEntryResolutionState.AMBIGUOUS,
            ReadingListEntryResolutionState.UNRESOLVED,
            ReadingListEntryResolutionState.SOURCE_UNAVAILABLE,
            ReadingListEntryResolutionState.CHAPTER_REMOVED,
            ReadingListEntryResolutionState.NEEDS_REMATCH,
            ReadingListEntryResolutionState.UNSEARCHED,
            ReadingListEntryResolutionState.SEARCHING,
            -> true
            ReadingListEntryResolutionState.AUTO_MATCHED,
            ReadingListEntryResolutionState.USER_CONFIRMED,
            -> false
        }
    }

sealed interface ReadingListReviewAction {
    data class ConfirmCandidate(
        val entryId: Long,
        val identity: ReadingListCandidateIdentity,
    ) : ReadingListReviewAction

    data class RejectCandidate(
        val entryId: Long,
        val identity: ReadingListCandidateIdentity,
    ) : ReadingListReviewAction

    data class RestoreCandidate(
        val entryId: Long,
        val identity: ReadingListCandidateIdentity,
    ) : ReadingListReviewAction

    data class ConfirmSeriesMapping(
        val entryId: Long,
        val identity: ReadingListCandidateIdentity,
    ) : ReadingListReviewAction

    data class ClearSeriesMapping(
        val entryId: Long,
    ) : ReadingListReviewAction
}

sealed interface ReadingListReviewEvent {
    data object CandidateConfirmed : ReadingListReviewEvent
    data object CandidateRejected : ReadingListReviewEvent
    data object CandidateRestored : ReadingListReviewEvent
    data object SeriesMappingConfirmed : ReadingListReviewEvent
    data object SeriesMappingCleared : ReadingListReviewEvent
    data object ReadingListMissing : ReadingListReviewEvent
    data object ActionFailed : ReadingListReviewEvent
}
