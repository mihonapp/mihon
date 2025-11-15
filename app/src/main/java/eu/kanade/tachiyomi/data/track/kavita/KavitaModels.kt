package eu.kanade.tachiyomi.data.track.kavita

import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import kotlinx.serialization.Serializable

@Serializable
enum class LibraryTypeEnum(val type: Int) {
    Manga(0),
    Comic(1),
    Book(2),
    Image(3),
    LightNovel(4),
    ComicVine(5),
    ;

    companion object {
        private val map = entries.associateBy(LibraryTypeEnum::type)
    }
}

@Serializable
data class SeriesDto(
    val id: Int,
    val name: String,
    val originalName: String = "",
    val thumbnail_url: String? = "",
    val localizedName: String? = "",
    val sortName: String? = "",
    val pages: Int,
    val coverImageLocked: Boolean = true,
    val pagesRead: Int,
    val userRating: Int? = 0,
    val userReview: String? = "",
    val format: Int,
    val created: String? = "",
    val libraryId: Int,
    val libraryName: String? = "",
) {
    fun toTrack(): TrackSearch = TrackSearch.create(TrackerManager.KAVITA).also {
        it.title = name
        it.summary = ""
    }
}

@Serializable
data class VolumeDto(
    val id: Int,
    val minNumber: Double,
    val maxNumber: Double,
    val name: String? = null,
    val pages: Int,
    val pagesRead: Int,
    val lastModified: String,
    val created: String,
    val seriesId: Int,
    val coverImage: String,
    val chapters: List<ChapterDto> = emptyList(),
)

@Serializable
data class ChapterDto(
    val id: Int,
    val range: String? = null,
    val minNumber: Double,
    val maxNumber: Double,
    val pages: Int,
    val isSpecial: Boolean,
    val title: String? = null,
    val titleName: String? = null,
    val pagesRead: Int,
    val coverImageLocked: Boolean,
    val coverImage: String? = null,
    val volumeId: Int,
    val created: String,
    val lastModifiedUtc: String,
    val files: List<FileDto>? = null,
) {
}

@Serializable
data class FileDto(
    val id: Int,
)

@Serializable
enum class ChapterType {
    Regular, // Chapter with volume information
    Chapter, // Chapter without volume information
    SingleFileVolume,
    Special,
    Issue, // For comics
    ;

    companion object {
        const val UNNUMBERED_VOLUME_NUMBER = -100_000
        const val SPECIAL_NUMBER = 100_000

        fun of(chapter: ChapterDto, volume: VolumeDto, libraryType: LibraryTypeEnum? = null): ChapterType =
            when {
                // Special
                volume.minNumber.toInt() == SPECIAL_NUMBER ||
                    chapter.minNumber.toInt() == SPECIAL_NUMBER -> Special
                // Issue
                volume.minNumber.toInt() == UNNUMBERED_VOLUME_NUMBER -> when (libraryType) {
                    LibraryTypeEnum.Comic, LibraryTypeEnum.ComicVine -> Issue
                    else -> Chapter
                }
                // SingleFileVolume
                chapter.minNumber.toInt() == UNNUMBERED_VOLUME_NUMBER -> SingleFileVolume
                // Regular
                volume.minNumber > 0 -> Regular
                // Everything else depends on library type
                else -> when (libraryType) {
                    LibraryTypeEnum.Comic, LibraryTypeEnum.ComicVine -> Issue
                    else -> Chapter
                }
            }
    }
}


@Serializable
data class AuthenticationDto(
    val username: String,
    val token: String,
    val apiKey: String,
)

class OAuth(
    val authentications: List<SourceAuth> = listOf(
        SourceAuth(1),
        SourceAuth(2),
        SourceAuth(3),
    ),
) {
    fun getToken(apiUrl: String): String? {
        for (authentication in authentications) {
            if (authentication.apiUrl == apiUrl) {
                return authentication.jwtToken
            }
        }
        return null
    }
}

data class SourceAuth(
    var sourceId: Int,
    var apiUrl: String = "",
    var jwtToken: String = "",
)
