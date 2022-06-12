package eu.kanade.domain.manga.model

import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.model.SManga
import tachiyomi.source.model.MangaInfo
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import eu.kanade.tachiyomi.data.database.models.Manga as DbManga

data class Manga(
    val id: Long,
    val source: Long,
    val favorite: Boolean,
    val lastUpdate: Long,
    val dateAdded: Long,
    val viewerFlags: Long,
    val chapterFlags: Long,
    val coverLastModified: Long,
    val url: String,
    val title: String,
    val artist: String?,
    val author: String?,
    val description: String?,
    val genre: List<String>?,
    val status: Long,
    val thumbnailUrl: String?,
    val initialized: Boolean,
) {

    val sorting: Long
        get() = chapterFlags and CHAPTER_SORTING_MASK

    fun toSManga(): SManga {
        return SManga.create().also {
            it.url = url
            it.title = title
            it.artist = artist
            it.author = author
            it.description = description
            it.genre = genre.orEmpty().joinToString()
            it.status = status.toInt()
            it.thumbnail_url = thumbnailUrl
            it.initialized = initialized
        }
    }

    companion object {

        // Generic filter that does not filter anything
        const val SHOW_ALL = 0x00000000L

        const val CHAPTER_SORTING_SOURCE = 0x00000000L
        const val CHAPTER_SORTING_NUMBER = 0x00000100L
        const val CHAPTER_SORTING_UPLOAD_DATE = 0x00000200L
        const val CHAPTER_SORTING_MASK = 0x00000300L
    }
}

// TODO: Remove when all deps are migrated
fun Manga.toDbManga(): DbManga = DbManga.create(url, title, source).also {
    it.id = id
    it.favorite = favorite
    it.last_update = lastUpdate
    it.date_added = dateAdded
    it.viewer_flags = viewerFlags.toInt()
    it.chapter_flags = chapterFlags.toInt()
    it.cover_last_modified = coverLastModified
}

fun Manga.toMangaInfo(): MangaInfo = MangaInfo(
    artist = artist ?: "",
    author = author ?: "",
    cover = thumbnailUrl ?: "",
    description = description ?: "",
    genres = genre ?: emptyList(),
    key = url,
    status = status.toInt(),
    title = title,
)

fun Manga.isLocal(): Boolean = source == LocalSource.ID

fun Manga.hasCustomCover(coverCache: CoverCache = Injekt.get()): Boolean {
    return coverCache.getCustomCoverFile(id).exists()
}
