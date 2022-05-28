package eu.kanade.data.history

import eu.kanade.domain.history.model.History
import eu.kanade.domain.history.model.HistoryWithRelations
import java.util.Date

val historyMapper: (Long, Long, Date?, Long) -> History = { id, chapterId, readAt, readDuration ->
    History(
        id = id,
        chapterId = chapterId,
        readAt = readAt,
        readDuration = readDuration,
    )
}

val historyWithRelationsMapper: (Long, Long, Long, String, String?, Float, Date?, Long) -> HistoryWithRelations = {
        historyId, mangaId, chapterId, title, thumbnailUrl, chapterNumber, readAt, readDuration ->
    HistoryWithRelations(
        id = historyId,
        chapterId = chapterId,
        mangaId = mangaId,
        title = title,
        thumbnailUrl = thumbnailUrl ?: "",
        chapterNumber = chapterNumber,
        readAt = readAt,
        readDuration = readDuration,
    )
}
