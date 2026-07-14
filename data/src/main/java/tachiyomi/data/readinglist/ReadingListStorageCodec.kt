package tachiyomi.data.readinglist

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tachiyomi.domain.readinglist.cbl.model.CblParseWarning
import tachiyomi.domain.readinglist.cbl.model.CblParseWarningCode
import tachiyomi.domain.readinglist.cbl.model.CblReadingList
import tachiyomi.domain.readinglist.matching.ConfirmedHistoryEvidence
import tachiyomi.domain.readinglist.matching.EvidenceAgreement
import tachiyomi.domain.readinglist.matching.MatchScoreBreakdown
import tachiyomi.domain.readinglist.matching.SourcePreferenceLevel

internal class ReadingListStorageCodec(
    private val json: Json,
) {

    fun encodeAttributes(value: Map<String, String>): String {
        return json.encodeToString(value)
    }

    fun decodeAttributes(value: String): Map<String, String> {
        return json.decodeFromString(value)
    }

    fun encodeElements(value: Map<String, List<String>>): String {
        return json.encodeToString(value)
    }

    fun decodeElements(value: String): Map<String, List<String>> {
        return json.decodeFromString(value)
    }

    fun encodeWarnings(value: List<CblParseWarning>): String {
        return json.encodeToString(
            value.map { warning ->
                StoredWarning(
                    code = warning.code.name,
                    message = warning.message,
                )
            },
        )
    }

    fun decodeWarnings(value: String): List<CblParseWarning> {
        return json.decodeFromString<List<StoredWarning>>(value).map { warning ->
            CblParseWarning(
                code = CblParseWarningCode.valueOf(warning.code),
                message = warning.message,
            )
        }
    }

    fun encodeMatchScoreBreakdown(value: MatchScoreBreakdown): String {
        return json.encodeToString(StoredMatchScoreBreakdown.from(value))
    }

    fun decodeMatchScoreBreakdown(value: String): MatchScoreBreakdown {
        return json.decodeFromString<StoredMatchScoreBreakdown>(value).toDomain()
    }

    @Serializable
    private data class StoredMatchScoreBreakdown(
        val titleSimilarity: Double,
        val titlePoints: Double,
        val issueEquivalent: Boolean,
        val issuePoints: Double,
        val yearEvidence: String,
        val yearPoints: Double,
        val volumeEvidence: String,
        val volumePoints: Double,
        val externalIdentifierEvidence: String,
        val externalIdentifierPoints: Double,
        val sourcePreference: String,
        val sourcePreferencePoints: Double,
        val confirmedHistory: String,
        val confirmedHistoryPoints: Double,
        val total: Double,
    ) {
        fun toDomain(): MatchScoreBreakdown {
            return MatchScoreBreakdown(
                titleSimilarity = titleSimilarity,
                titlePoints = titlePoints,
                issueEquivalent = issueEquivalent,
                issuePoints = issuePoints,
                yearEvidence = EvidenceAgreement.valueOf(yearEvidence),
                yearPoints = yearPoints,
                volumeEvidence = EvidenceAgreement.valueOf(volumeEvidence),
                volumePoints = volumePoints,
                externalIdentifierEvidence = EvidenceAgreement.valueOf(externalIdentifierEvidence),
                externalIdentifierPoints = externalIdentifierPoints,
                sourcePreference = SourcePreferenceLevel.valueOf(sourcePreference),
                sourcePreferencePoints = sourcePreferencePoints,
                confirmedHistory = ConfirmedHistoryEvidence.valueOf(confirmedHistory),
                confirmedHistoryPoints = confirmedHistoryPoints,
                total = total,
            )
        }

        companion object {
            fun from(value: MatchScoreBreakdown): StoredMatchScoreBreakdown {
                return StoredMatchScoreBreakdown(
                    titleSimilarity = value.titleSimilarity,
                    titlePoints = value.titlePoints,
                    issueEquivalent = value.issueEquivalent,
                    issuePoints = value.issuePoints,
                    yearEvidence = value.yearEvidence.name,
                    yearPoints = value.yearPoints,
                    volumeEvidence = value.volumeEvidence.name,
                    volumePoints = value.volumePoints,
                    externalIdentifierEvidence = value.externalIdentifierEvidence.name,
                    externalIdentifierPoints = value.externalIdentifierPoints,
                    sourcePreference = value.sourcePreference.name,
                    sourcePreferencePoints = value.sourcePreferencePoints,
                    confirmedHistory = value.confirmedHistory.name,
                    confirmedHistoryPoints = value.confirmedHistoryPoints,
                    total = value.total,
                )
            }
        }
    }

    @Serializable
    private data class StoredWarning(
        val code: String,
        val message: String,
    )
}

internal fun CblReadingList.requireValidPersistenceOrder() {
    books.forEachIndexed { index, book ->
        require(book.position == index) {
            "CBL book position ${book.position} does not match authoritative list index $index"
        }
        require(book.series.isNotBlank()) {
            "CBL book at position $index has a blank series"
        }
        require(book.number.isNotBlank()) {
            "CBL book at position $index has a blank issue number"
        }
    }
}

internal fun List<Long>.requireValidSourceSelection() {
    require(isNotEmpty()) {
        "At least one source must be selected for a reading list"
    }
    require(size == distinct().size) {
        "Reading-list source selection contains duplicate source IDs"
    }
}
