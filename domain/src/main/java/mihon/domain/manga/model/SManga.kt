package mihon.domain.manga.model

import eu.kanade.tachiyomi.source.model.SManga
import tachiyomi.domain.manga.model.Manga

fun SManga.toDomainManga(sourceId: Long): Manga {
    return Manga.create().copy(
        url = url,
        ogTitle = title,
        ogArtist = artist,
        ogAuthor = author,
        ogDescription = description,
        ogGenre = getGenres(),
        ogStatus = status.toLong(),
        ogThumbnailUrl = thumbnail_url,
        updateStrategy = update_strategy,
        initialized = initialized,
        source = sourceId,
    )
}
