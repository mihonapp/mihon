package tachiyomi.data.manga

import eu.kanade.tachiyomi.source.model.UpdateStrategy
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.model.Manga

val mangaMapper: (Long, Long, String, String?, String?, String?, List<String>?, String, Long, String?, Boolean, Long?, Long?, Boolean, Long, Long, Long, Long, UpdateStrategy, Long, Long?) -> Manga =
    { id, source, url, artist, author, description, genre, title, status, thumbnailUrl, favorite, lastUpdate, nextUpdate, initialized, viewerFlags, chapterFlags, coverLastModified, dateAdded, updateStrategy, calculateInterval, lastModifiedAt ->
        Manga(
            id = id,
            source = source,
            favorite = favorite,
            lastUpdate = lastUpdate ?: 0,
            nextUpdate = nextUpdate ?: 0,
            calculateInterval = calculateInterval.toInt(),
            dateAdded = dateAdded,
            viewerFlags = viewerFlags,
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
            lastModifiedAt = lastModifiedAt,
        )
    }

val libraryManga: (Long, Long, String, String?, String?, String?, List<String>?, String, Long, String?, Boolean, Long?, Long?, Boolean, Long, Long, Long, Long, UpdateStrategy, Long, Long?, Long, Long, Long, Long, Long, Long, Long) -> LibraryManga =
    { id, source, url, artist, author, description, genre, title, status, thumbnailUrl, favorite, lastUpdate, nextUpdate, initialized, viewerFlags, chapterFlags, coverLastModified, dateAdded, updateStrategy, calculateInterval, lastModifiedAt, totalCount, readCount, latestUpload, chapterFetchedAt, lastRead, bookmarkCount, category ->
        LibraryManga(
            manga = mangaMapper(
                id,
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
                calculateInterval,
                lastModifiedAt,
            ),
            category = category,
            totalChapters = totalCount,
            readCount = readCount,
            bookmarkCount = bookmarkCount,
            latestUpload = latestUpload,
            chapterFetchedAt = chapterFetchedAt,
            lastRead = lastRead,
        )
    }
