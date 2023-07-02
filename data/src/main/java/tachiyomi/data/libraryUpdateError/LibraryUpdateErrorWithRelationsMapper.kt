package tachiyomi.data.libraryUpdateError

import tachiyomi.domain.libraryUpdateError.model.LibraryUpdateErrorWithRelations
import tachiyomi.domain.manga.model.MangaCover

val libraryUpdateErrorWithRelationsMapper:
    (Long, String, Long, Boolean, String?, Long, Long, Long) -> LibraryUpdateErrorWithRelations =
    { mangaId, mangaTitle, mangaSource, favorite, mangaThumbnail, coverLastModified, errorId, messageId ->
        LibraryUpdateErrorWithRelations(
            mangaId = mangaId,
            mangaTitle = mangaTitle,
            mangaSource = mangaSource,
            mangaCover = MangaCover(
                mangaId = mangaId,
                sourceId = mangaSource,
                isMangaFavorite = favorite,
                url = mangaThumbnail,
                lastModified = coverLastModified,
            ),
            errorId = errorId,
            messageId = messageId,
        )
    }
