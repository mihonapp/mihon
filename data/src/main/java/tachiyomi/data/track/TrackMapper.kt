package tachiyomi.data.track

import tachiyomi.domain.track.model.Track

val trackMapper: (Long, Long, Long, Long, Long?, String, Double, Long, Long, Double, String, Long, Long) -> Track =
    { id, mangaId, syncId, remoteId, libraryId, title, lastChapterRead, totalChapters, status, score, remoteUrl, startDate, finishDate ->
        Track(
            id = id,
            mangaId = mangaId,
            syncId = syncId,
            remoteId = remoteId,
            libraryId = libraryId,
            title = title,
            lastChapterRead = lastChapterRead,
            totalChapters = totalChapters,
            status = status,
            score = score,
            remoteUrl = remoteUrl,
            startDate = startDate,
            finishDate = finishDate,
        )
    }
