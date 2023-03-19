package eu.kanade.tachiyomi.source.model

import tachiyomi.data.Mangas
import tachiyomi.domain.manga.model.Manga

fun SManga.copyFrom(other: Mangas) {
    if (other.author != null) {
        author = other.author
    }

    if (other.artist != null) {
        artist = other.artist
    }

    if (other.description != null) {
        description = other.description
    }

    if (other.genre != null) {
        genre = other.genre!!.joinToString(separator = ", ")
    }

    if (other.thumbnail_url != null) {
        thumbnail_url = other.thumbnail_url
    }

    status = other.status.toInt()

    if (!initialized) {
        initialized = other.initialized
    }
}

fun Manga.copyFrom(other: Mangas): Manga {
    var manga = this
    if (other.author != null) {
        manga = manga.copy(author = other.author)
    }

    if (other.artist != null) {
        manga = manga.copy(artist = other.artist)
    }

    if (other.description != null) {
        manga = manga.copy(description = other.description)
    }

    if (other.genre != null) {
        manga = manga.copy(genre = other.genre)
    }

    if (other.thumbnail_url != null) {
        manga = manga.copy(thumbnailUrl = other.thumbnail_url)
    }

    manga = manga.copy(status = other.status)

    if (!initialized) {
        manga = manga.copy(initialized = other.initialized)
    }
    return manga
}
