package tachiyomi.data.history

import tachiyomi.domain.history.model.History
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.domain.manga.model.MangaCover
import java.util.Date

val historyMapper: (Long, Long, Date?, Long) -> History = { id, chapterId, readAt, readDuration ->
    History(
        id = id,
        chapterId = chapterId,
        readAt = readAt,
        readDuration = readDuration,
    )
}

val historyWithRelationsMapper: (Long, Long, Long, String, String?, Long, Boolean, Long, Double, Date?, Long) -> HistoryWithRelations = {
        historyId, mangaId, chapterId, title, thumbnailUrl, sourceId, isFavorite, coverLastModified, chapterNumber, readAt, readDuration ->
    HistoryWithRelations(
        id = historyId,
        chapterId = chapterId,
        mangaId = mangaId,
        title = title,
        chapterNumber = chapterNumber,
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
