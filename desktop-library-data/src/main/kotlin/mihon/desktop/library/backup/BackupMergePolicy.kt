package mihon.desktop.library.backup

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import mihon.desktop.library.model.ChapterRecord
import mihon.desktop.library.model.HistoryRecord
import mihon.desktop.library.model.MangaRecord
import mihon.desktop.library.model.TrackingRecord

object BackupMergePolicy {
    fun mergeManga(existing: MangaRecord, incoming: MangaRecord): MangaRecord {
        val incomingWins = incoming.lastModifiedAt > existing.lastModifiedAt
        val selected = if (incomingWins) incoming else existing
        return existing.copy(
            title = selectedString(existing.title, incoming.title, incomingWins),
            artist = selectedNullableString(existing.artist, incoming.artist, incomingWins),
            author = selectedNullableString(existing.author, incoming.author, incomingWins),
            description = selectedNullableString(existing.description, incoming.description, incomingWins),
            genreJson = unionJsonStringSets(existing.genreJson, incoming.genreJson),
            status = selected.status,
            thumbnailUrl = selectedNullableString(existing.thumbnailUrl, incoming.thumbnailUrl, incomingWins),
            favorite = existing.favorite || incoming.favorite,
            dateAdded = selected.dateAdded,
            viewerFlags = selected.viewerFlags,
            chapterFlags = selected.chapterFlags,
            updateStrategy = selectedString(existing.updateStrategy, incoming.updateStrategy, incomingWins),
            lastModifiedAt = maxOf(existing.lastModifiedAt, incoming.lastModifiedAt),
            favoriteModifiedAt = maxNullable(existing.favoriteModifiedAt, incoming.favoriteModifiedAt),
            excludedScanlatorsJson = unionJsonStringSets(
                existing.excludedScanlatorsJson,
                incoming.excludedScanlatorsJson,
            ),
            version = maxOf(existing.version, incoming.version),
            notes = selectedString(existing.notes, incoming.notes, incomingWins),
            initialized = selected.initialized,
            memoJson = selectedString(existing.memoJson, incoming.memoJson, incomingWins),
        )
    }

    fun mergeChapter(existing: ChapterRecord, incoming: ChapterRecord): ChapterRecord {
        val incomingWins = incoming.lastModifiedAt > existing.lastModifiedAt
        val selected = if (incomingWins) incoming else existing
        return existing.copy(
            name = selectedString(existing.name, incoming.name, incomingWins),
            scanlator = selectedNullableString(existing.scanlator, incoming.scanlator, incomingWins),
            read = existing.read || incoming.read,
            bookmark = existing.bookmark || incoming.bookmark,
            lastPageRead = maxOf(existing.lastPageRead, incoming.lastPageRead),
            dateFetch = maxOf(existing.dateFetch, incoming.dateFetch),
            dateUpload = maxOf(existing.dateUpload, incoming.dateUpload),
            chapterNumber = selected.chapterNumber,
            sourceOrder = selected.sourceOrder,
            lastModifiedAt = maxOf(existing.lastModifiedAt, incoming.lastModifiedAt),
            version = maxOf(existing.version, incoming.version),
            memoJson = selectedString(existing.memoJson, incoming.memoJson, incomingWins),
        )
    }

    fun mergeHistory(existing: HistoryRecord, incoming: HistoryRecord): HistoryRecord = existing.copy(
        lastRead = maxOf(existing.lastRead, incoming.lastRead),
        readDuration = maxOf(existing.readDuration, incoming.readDuration),
    )

    fun mergeTracking(existing: TrackingRecord, incoming: TrackingRecord): TrackingRecord {
        val incomingWins = incoming.lastChapterRead > existing.lastChapterRead
        val selected = if (incomingWins) incoming else existing
        return existing.copy(
            remoteId = selected.remoteId,
            libraryId = selected.libraryId,
            title = selectedString(existing.title, incoming.title, incomingWins),
            lastChapterRead = maxOf(existing.lastChapterRead, incoming.lastChapterRead),
            totalChapters = maxOf(existing.totalChapters, incoming.totalChapters),
            score = selected.score,
            status = selected.status,
            startedReadingDate = earliestNonzero(existing.startedReadingDate, incoming.startedReadingDate),
            finishedReadingDate = maxOf(existing.finishedReadingDate, incoming.finishedReadingDate),
            private = selected.private,
            trackingUrl = selectedString(existing.trackingUrl, incoming.trackingUrl, incomingWins),
        )
    }

    private fun selectedString(existing: String, incoming: String, incomingWins: Boolean): String =
        if (incomingWins && incoming.isNotBlank()) incoming else existing

    private fun selectedNullableString(existing: String?, incoming: String?, incomingWins: Boolean): String? =
        if (incomingWins && !incoming.isNullOrBlank()) incoming else existing

    private fun earliestNonzero(existing: Long, incoming: Long): Long = when {
        existing == 0L -> incoming
        incoming == 0L -> existing
        else -> minOf(existing, incoming)
    }

    private fun maxNullable(existing: Long?, incoming: Long?): Long? = when {
        existing == null -> incoming
        incoming == null -> existing
        else -> maxOf(existing, incoming)
    }

    private fun unionJsonStringSets(existing: String, incoming: String): String {
        val values = buildSet {
            addAll(Json.decodeFromString<List<String>>(existing))
            addAll(Json.decodeFromString<List<String>>(incoming))
        }.sortedWith(UNICODE_CODE_POINT_COMPARATOR)
        return JsonArray(values.map(::JsonPrimitive)).toString()
    }
}
