package eu.kanade.data.track

import eu.kanade.domain.track.model.Track

val trackMapper: (Long, Long, Long, Long, Long?, String, Double, Long, Long, Float, String, Long, Long) -> Track =
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
