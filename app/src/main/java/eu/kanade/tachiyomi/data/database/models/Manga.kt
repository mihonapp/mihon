package eu.kanade.tachiyomi.data.database.models

import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.ui.reader.setting.OrientationType
import eu.kanade.tachiyomi.ui.reader.setting.ReadingModeType
import tachiyomi.domain.manga.model.Manga as DomainManga

interface Manga : SManga {

    var id: Long?

    var source: Long

    var favorite: Boolean

    // last time the chapter list changed in any way
    var last_update: Long

    var date_added: Long

    var viewer_flags: Int

    var chapter_flags: Int

    var cover_last_modified: Long

    private fun setViewerFlags(flag: Int, mask: Int) {
        viewer_flags = viewer_flags and mask.inv() or (flag and mask)
    }

    var readingModeType: Int
        get() = viewer_flags and ReadingModeType.MASK
        set(readingMode) = setViewerFlags(readingMode, ReadingModeType.MASK)

    var orientationType: Int
        get() = viewer_flags and OrientationType.MASK
        set(rotationType) = setViewerFlags(rotationType, OrientationType.MASK)
}

fun Manga.toDomainManga(): DomainManga? {
    val mangaId = id ?: return null
    return DomainManga(
        id = mangaId,
        source = source,
        favorite = favorite,
        lastUpdate = last_update,
        dateAdded = date_added,
        viewerFlags = viewer_flags.toLong(),
        chapterFlags = chapter_flags.toLong(),
        coverLastModified = cover_last_modified,
        url = url,
        title = title,
        artist = artist,
        author = author,
        description = description,
        genre = getGenres(),
        status = status.toLong(),
        thumbnailUrl = thumbnail_url,
        updateStrategy = update_strategy,
        initialized = initialized,
    )
}
