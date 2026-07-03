package tachiyomi.data.history

import tachiyomi.domain.history.model.History
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.domain.manga.model.MangaCover
import java.util.Date

object HistoryMapper {
    fun mapHistory(
        id: Long,
        chapterId: Long,
        readAt: Date?,
        readDuration: Long,
    ): History = History(
        id = id,
        chapterId = chapterId,
        readAt = readAt,
        readDuration = readDuration,
    )

    fun mapHistoryWithRelations(
        historyId: Long,
        mangaId: Long,
        chapterId: Long,
        title: String,
        thumbnailUrl: String?,
        sourceId: Long,
        isFavorite: Boolean,
        coverLastModified: Long,
        chapterNumber: Double,
        // KMK -->
        read: Boolean,
        lastPageRead: Long,
        totalCount: Long,
        readCount: Double,
        // KMK <--
        readAt: Date?,
        readDuration: Long,
    ): HistoryWithRelations = HistoryWithRelations(
        id = historyId,
        chapterId = chapterId,
        mangaId = mangaId,
        title = title,
        chapterNumber = chapterNumber,
        // KMK -->
        read = read,
        lastPageRead = lastPageRead,
        totalChapters = totalCount,
        readCount = readCount.toLong(),
        // KMK <--
        readAt = readAt,
        readDuration = readDuration,
        coverData = MangaCover(
            mangaId = mangaId,
            sourceId = sourceId,
            isMangaFavorite = isFavorite,
            url = thumbnailUrl,
            lastModified = coverLastModified,
        ),
    )
}
