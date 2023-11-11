package eu.kanade.tachiyomi.source.model

import tachiyomi.data.Mangas
import tachiyomi.domain.manga.model.Manga

fun Manga.copyFrom(other: Mangas): Manga {
    var manga = this
    other.author?.let { manga = manga.copy(author = it) }
    other.artist?.let { manga = manga.copy(artist = it) }
    other.description?.let { manga = manga.copy(description = it) }
    other.genre?.let { manga = manga.copy(genre = it) }
    other.thumbnail_url?.let { manga = manga.copy(thumbnailUrl = it) }
    manga = manga.copy(status = other.status)
    if (!initialized) {
        manga = manga.copy(initialized = other.initialized)
    }
    return manga
}
