package tachiyomi.domain.readinglist.model

import tachiyomi.domain.readinglist.cbl.model.CblBook
import tachiyomi.domain.readinglist.cbl.model.CblDatabaseReference
import tachiyomi.domain.readinglist.cbl.model.CblParseWarning
import tachiyomi.domain.readinglist.cbl.model.CblReadingList

data class ReadingList(
    val id: Long,
    val name: String?,
    val description: String?,
    val declaredIssueCount: Int?,
    val entries: List<ReadingListEntry>,
    val selectedSourceIds: List<Long>,
    val extraAttributes: Map<String, String>,
    val extraElements: Map<String, List<String>>,
    val warnings: List<CblParseWarning>,
    val currentPosition: Int?,
    val createdAt: Long,
    val updatedAt: Long,
) {
    fun toCblReadingList(): CblReadingList {
        return CblReadingList(
            name = name,
            description = description,
            declaredIssueCount = declaredIssueCount,
            books = entries.map(ReadingListEntry::toCblBook),
            extraAttributes = extraAttributes,
            extraElements = extraElements,
            warnings = warnings,
        )
    }
}

data class ReadingListEntry(
    val id: Long,
    val readingListId: Long,
    val position: Int,
    val series: String,
    val number: String,
    val volume: String?,
    val year: String?,
    val databases: List<CblDatabaseReference>,
    val extraAttributes: Map<String, String>,
    val extraElements: Map<String, List<String>>,
    val resolutionState: ReadingListEntryResolutionState,
    val matchedSourceId: Long?,
    val matchedMangaUrl: String?,
    val matchedChapterUrl: String?,
    val confidence: Double?,
    val matcherVersion: Long?,
    val userConfirmed: Boolean,
    val skipped: Boolean,
) {
    fun toCblBook(): CblBook {
        return CblBook(
            position = position,
            series = series,
            number = number,
            volume = volume,
            year = year,
            databases = databases,
            extraAttributes = extraAttributes,
            extraElements = extraElements,
        )
    }
}

data class ReadingListSummary(
    val id: Long,
    val name: String?,
    val entryCount: Int,
    val sourceCount: Int,
    val currentPosition: Int?,
    val createdAt: Long,
    val updatedAt: Long,
)

enum class ReadingListEntryResolutionState {
    UNSEARCHED,
    SEARCHING,
    AUTO_MATCHED,
    USER_CONFIRMED,
    AMBIGUOUS,
    UNRESOLVED,
    SOURCE_UNAVAILABLE,
    CHAPTER_REMOVED,
    NEEDS_REMATCH,
}
