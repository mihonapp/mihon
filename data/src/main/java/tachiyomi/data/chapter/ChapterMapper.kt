package tachiyomi.data.chapter

import tachiyomi.domain.chapter.model.Chapter

val chapterMapper: (Long, Long, String, String, String?, Boolean, Boolean, Long, Double, Long, Long, Long, Long) -> Chapter =
    { id, mangaId, url, name, scanlator, read, bookmark, lastPageRead, chapterNumber, sourceOrder, dateFetch, dateUpload, lastModifiedAt ->
        Chapter(
            id = id,
            mangaId = mangaId,
            read = read,
            bookmark = bookmark,
            lastPageRead = lastPageRead,
            dateFetch = dateFetch,
            sourceOrder = sourceOrder,
            url = url,
            name = name,
            dateUpload = dateUpload,
            chapterNumber = chapterNumber,
            scanlator = scanlator,
            lastModifiedAt = lastModifiedAt,
        )
    }
