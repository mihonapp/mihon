package mihon.data.repository.manga.local

import eu.kanade.tachiyomi.source.model.SManga

object LocalSMangaMapper {
    fun mapSManga(
        url: String,
        artist: String?,
        author: String?,
        description: String?,
        genre: List<String>?,
        title: String,
        status: Long,
        thumbnailUrl: String?,
        dirLastModified: Long?,
    ): SManga = SManga.create().also {
        it.url = url
        it.title = title
        it.artist = artist
        it.author = author
        it.description = description
        it.genre = genre?.joinToString()
        it.status = status.toInt()
        it.thumbnail_url = thumbnailUrl
        it.dir_last_modified = dirLastModified
    }
}
