package tachiyomi.domain.readinglist.model

import tachiyomi.domain.readinglist.matching.MatchDecisionReason
import tachiyomi.domain.readinglist.matching.MatchScoreBreakdown

data class ReadingListCandidateIdentity(
    val sourceId: Long,
    val candidateId: String,
) {
    init {
        require(candidateId.isNotBlank()) {
            "Reading-list candidate ID cannot be blank"
        }
    }
}

data class ReadingListMatchCandidateSnapshot(
    val identity: ReadingListCandidateIdentity,
    val sourceName: String,
    val sourceLanguage: String,
    val mangaUrl: String,
    val chapterUrl: String,
    val seriesTitle: String,
    val issueNumber: String,
    val volume: Int?,
    val year: Int?,
    val breakdown: MatchScoreBreakdown,
    val decisionReason: MatchDecisionReason,
    val leadOverRunnerUp: Double?,
    val matcherVersion: Long,
) {
    init {
        require(sourceName.isNotBlank()) {
            "Reading-list candidate source name cannot be blank"
        }
        require(sourceLanguage.isNotBlank()) {
            "Reading-list candidate source language cannot be blank"
        }
        require(mangaUrl.isNotBlank()) {
            "Reading-list candidate manga URL cannot be blank"
        }
        require(chapterUrl.isNotBlank()) {
            "Reading-list candidate chapter URL cannot be blank"
        }
        require(seriesTitle.isNotBlank()) {
            "Reading-list candidate series title cannot be blank"
        }
        require(issueNumber.isNotBlank()) {
            "Reading-list candidate issue number cannot be blank"
        }
        require(leadOverRunnerUp == null || leadOverRunnerUp in 0.0..100.0) {
            "Reading-list candidate lead must be between 0 and 100"
        }
        require(matcherVersion > 0) {
            "Reading-list matcher version must be positive"
        }
    }

    val score: Double
        get() = breakdown.total
}

data class ReadingListStoredMatchCandidate(
    val entryId: Long,
    val snapshot: ReadingListMatchCandidateSnapshot,
    val rejected: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

data class ReadingListCandidateRejection(
    val entryId: Long,
    val identity: ReadingListCandidateIdentity,
    val mangaUrl: String,
    val chapterUrl: String,
    val rejectedAt: Long,
) {
    init {
        require(mangaUrl.isNotBlank()) {
            "Rejected-candidate manga URL cannot be blank"
        }
        require(chapterUrl.isNotBlank()) {
            "Rejected-candidate chapter URL cannot be blank"
        }
    }
}

data class ReadingListEntryOverride(
    val entryId: Long,
    val sourceId: Long,
    val mangaUrl: String?,
    val chapterUrl: String?,
    val createdAt: Long,
    val updatedAt: Long,
) {
    init {
        requireValidEntryOverrideUrls(mangaUrl, chapterUrl)
        require(updatedAt >= createdAt) {
            "Entry-override update time cannot precede creation"
        }
    }
}

data class ReadingListEntryOverrideUpdate(
    val sourceId: Long,
    val mangaUrl: String?,
    val chapterUrl: String?,
) {
    init {
        requireValidEntryOverrideUrls(mangaUrl, chapterUrl)
    }
}

data class ReadingListSeriesMapping(
    val readingListId: Long,
    val seriesKey: String,
    val seriesTitle: String,
    val sourceId: Long,
    val mangaUrl: String,
    val userConfirmed: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
) {
    init {
        requireValidSeriesMapping(seriesKey, seriesTitle, mangaUrl)
        require(updatedAt >= createdAt) {
            "Series-mapping update time cannot precede creation"
        }
    }
}

data class ReadingListSeriesMappingUpdate(
    val seriesKey: String,
    val seriesTitle: String,
    val sourceId: Long,
    val mangaUrl: String,
) {
    init {
        requireValidSeriesMapping(seriesKey, seriesTitle, mangaUrl)
    }
}

data class ReadingListAutomaticResolutionUpdate(
    val state: ReadingListEntryResolutionState,
    val leadingConfidence: Double?,
    val matcherVersion: Long,
    val acceptedCandidate: ReadingListMatchCandidateSnapshot?,
) {
    init {
        require(matcherVersion > 0) {
            "Reading-list matcher version must be positive"
        }
        require(leadingConfidence == null || leadingConfidence in 0.0..100.0) {
            "Leading confidence must be between 0 and 100"
        }

        when (state) {
            ReadingListEntryResolutionState.AUTO_MATCHED -> {
                val candidate = requireNotNull(acceptedCandidate) {
                    "Automatic matches require an accepted candidate"
                }
                require(candidate.matcherVersion == matcherVersion) {
                    "Accepted-candidate matcher version must match the update"
                }
                require(candidate.decisionReason == MatchDecisionReason.AUTO_ACCEPTED) {
                    "Automatic matches require an AUTO_ACCEPTED candidate"
                }
                require(leadingConfidence == candidate.score) {
                    "Automatic-match confidence must equal the accepted candidate score"
                }
            }
            ReadingListEntryResolutionState.AMBIGUOUS -> {
                require(acceptedCandidate == null) {
                    "Ambiguous results cannot persist an accepted candidate"
                }
                requireNotNull(leadingConfidence) {
                    "Ambiguous results require the leading candidate confidence"
                }
            }
            ReadingListEntryResolutionState.UNRESOLVED -> {
                require(acceptedCandidate == null) {
                    "Unresolved results cannot persist an accepted candidate"
                }
            }
            else -> {
                throw IllegalArgumentException(
                    "Automatic resolution cannot write state ${state.name}",
                )
            }
        }
    }
}

enum class ReadingListProtectedWriteResult {
    APPLIED,
    ENTRY_NOT_FOUND,
    USER_CONFIRMED,
}

enum class ReadingListSeriesMappingWriteResult {
    APPLIED,
    READING_LIST_NOT_FOUND,
    USER_CONFIRMED,
}

data class ReadingListResolutionData(
    val readingListId: Long,
    val candidates: List<ReadingListStoredMatchCandidate>,
    val rejections: List<ReadingListCandidateRejection>,
    val entryOverrides: List<ReadingListEntryOverride>,
    val seriesMappings: List<ReadingListSeriesMapping>,
)

private fun requireValidEntryOverrideUrls(
    mangaUrl: String?,
    chapterUrl: String?,
) {
    require(mangaUrl == null || mangaUrl.isNotBlank()) {
        "Entry-override manga URL cannot be blank"
    }
    require(chapterUrl == null || chapterUrl.isNotBlank()) {
        "Entry-override chapter URL cannot be blank"
    }
    require(chapterUrl == null || mangaUrl != null) {
        "A chapter override requires a manga override"
    }
}

private fun requireValidSeriesMapping(
    seriesKey: String,
    seriesTitle: String,
    mangaUrl: String,
) {
    require(seriesKey.isNotBlank()) {
        "Series mapping key cannot be blank"
    }
    require(seriesTitle.isNotBlank()) {
        "Series mapping title cannot be blank"
    }
    require(mangaUrl.isNotBlank()) {
        "Series mapping manga URL cannot be blank"
    }
}
