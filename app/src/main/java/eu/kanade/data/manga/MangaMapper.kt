package eu.kanade.data.manga

import eu.kanade.domain.library.model.LibraryManga
import eu.kanade.domain.manga.model.Manga
import eu.kanade.tachiyomi.source.model.UpdateStrategy

val mangaMapper: (Long, Long, String, String?, String?, String?, List<String>?, String, Long, String?, Boolean, Long?, Long?, Boolean, Long, Long, Long, Long, UpdateStrategy) -> Manga =
    { id, source, url, artist, author, description, genre, title, status, thumbnailUrl, favorite, lastUpdate, _, initialized, viewer, chapterFlags, coverLastModified, dateAdded, updateStrategy ->
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
            updateStrategy = updateStrategy,
            initialized = initialized,
        )
    }

val libraryManga: (Long, Long, String, String?, String?, String?, List<String>?, String, Long, String?, Boolean, Long?, Long?, Boolean, Long, Long, Long, Long, UpdateStrategy, Long, Long, Long) -> LibraryManga =
    { _id, source, url, artist, author, description, genre, title, status, thumbnailUrl, favorite, lastUpdate, nextUpdate, initialized, viewerFlags, chapterFlags, coverLastModified, dateAdded, updateStrategy, unreadCount, readCount, category ->
        LibraryManga(
            manga = mangaMapper(
                _id,
                source,
                url,
                artist,
                author,
                description,
                genre,
                title,
                status,
                thumbnailUrl,
                favorite,
                lastUpdate,
                nextUpdate,
                initialized,
                viewerFlags,
                chapterFlags,
                coverLastModified,
                dateAdded,
                updateStrategy,
            ),
            category = category,
            unreadCount = unreadCount,
            readCount = readCount,
        )
    }
