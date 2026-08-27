package eu.kanade.tachiyomi.data.track.komga

import kotlinx.serialization.Serializable

@Serializable
data class SeriesDto(
    val id: String,
    val libraryId: String,
    val name: String,
    val created: String?,
    val lastModified: String?,
    val fileLastModified: String,
    val booksCount: Int,
    val booksReadCount: Int,
    val booksUnreadCount: Int,
    val booksInProgressCount: Int,
    val metadata: SeriesMetadataDto,
    val booksMetadata: BookMetadataAggregationDto,
)

@Serializable
data class SeriesMetadataDto(
    val status: String,
    val created: String?,
    val lastModified: String?,
    val title: String,
    val titleSort: String,
    val summary: String,
    val summaryLock: Boolean,
    val readingDirection: String,
    val readingDirectionLock: Boolean,
    val publisher: String,
    val publisherLock: Boolean,
    val ageRating: Int?,
    val ageRatingLock: Boolean,
    val language: String,
    val languageLock: Boolean,
    val genres: Set<String>,
    val genresLock: Boolean,
    val tags: Set<String>,
    val tagsLock: Boolean,
)

@Serializable
data class BookMetadataAggregationDto(
    val authors: List<AuthorDto> = emptyList(),
    val releaseDate: String?,
    val summary: String,
    val summaryNumber: String,

    val created: String,
    val lastModified: String,
)

@Serializable
data class AuthorDto(
    val name: String,
    val role: String,
)

@Serializable
data class ReadProgressUpdateDto(
    val lastBookRead: Int,
)

@Serializable
data class ReadProgressUpdateV2Dto(
    val lastBookNumberSortRead: Double,
)

@Serializable
data class ReadListDto(
    val id: String,
    val name: String,
    val bookIds: List<String>,
    val createdDate: String,
    val lastModifiedDate: String,
    val filtered: Boolean,
)

@Serializable
data class ReadProgressDto(
    val booksCount: Int,
    val booksReadCount: Int,
    val booksUnreadCount: Int,
    val booksInProgressCount: Int,
    val lastReadContinuousIndex: Int,
) {
    fun toV2() = ReadProgressV2Dto(
        booksCount,
        booksReadCount,
        booksUnreadCount,
        booksInProgressCount,
        lastReadContinuousIndex.toDouble(),
        booksCount.toFloat(),
    )
}

@Serializable
data class ReadProgressV2Dto(
    val booksCount: Int,
    val booksReadCount: Int,
    val booksUnreadCount: Int,
    val booksInProgressCount: Int,
    val lastReadContinuousNumberSort: Double,
    val maxNumberSort: Float,
)

@Serializable
data class BookDto(
    val readProgress: BookReadProgressDto? = null,
)

@Serializable
data class BookReadProgressDto(
    val page: Int,
    val completed: Boolean,
)

@Serializable
data class BookReadProgressUpdateDto(
    val page: Int,
    val completed: Boolean,
)

/**
 * Result of a page-level read-progress pull for a single book. [Absent] means Komga has no
 * progress recorded (e.g. after being marked unread), distinct from [Recorded] at page 0.
 */
sealed interface KomgaBookProgress {
    data class Recorded(val page: Int) : KomgaBookProgress
    data object Absent : KomgaBookProgress
}
