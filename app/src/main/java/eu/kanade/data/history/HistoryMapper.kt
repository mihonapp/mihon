package eu.kanade.data.history

import eu.kanade.domain.history.model.History
import eu.kanade.domain.history.model.HistoryWithRelations
import java.util.Date

val historyMapper: (Long, Long, Date?, Date?) -> History = { id, chapterId, readAt, _ ->
    History(
        id = id,
        chapterId = chapterId,
        readAt = readAt,
    )
}

val historyWithRelationsMapper: (Long, Long, Long, String, String?, Float, Date?) -> HistoryWithRelations = {
        historyId, mangaId, chapterId, title, thumbnailUrl, chapterNumber, readAt ->
    HistoryWithRelations(
        id = historyId,
        chapterId = chapterId,
        mangaId = mangaId,
        title = title,
        thumbnailUrl = thumbnailUrl ?: "",
        chapterNumber = chapterNumber,
        readAt = readAt,
    )
}
