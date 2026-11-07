package tachiyomi.domain.chapter.model

import kotlin.time.Instant

data class ChapterThumbnail(
    val chapterId: Long,
    val sourceId: Long,
    val isMangaFavorite: Boolean,
    val url: String?,
    val lastModified: Instant?,
)
