package eu.kanade.data.manga

import eu.kanade.domain.manga.model.Manga

val mangaMapper: (Long, Long, String, String?, String?, String?, List<String>?, String, Long, String?, Boolean, Long?, Long?, Boolean, Long, Long, Long, Long) -> Manga =
    { id, source, url, artist, author, description, genre, title, status, thumbnailUrl, favorite, lastUpdate, _, initialized, viewer, chapterFlags, coverLastModified, dateAdded ->
        Manga(
            id = id,
            source = source,
            favorite = favorite,
            lastUpdate = lastUpdate ?: 0,
            dateAdded = dateAdded,
            viewerFlags = viewer,
            chapterFlags = chapterFlags,
            coverLastModified = coverLastModified,
            url = url,
            title = title,
            artist = artist,
            author = author,
            description = description,
            genre = genre,
            status = status,
            thumbnailUrl = thumbnailUrl,
            initialized = initialized,
        )
    }
