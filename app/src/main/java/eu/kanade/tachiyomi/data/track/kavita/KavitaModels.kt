package eu.kanade.tachiyomi.data.track.kavita

import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import kotlinx.serialization.Serializable

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
    fun toTrack(): TrackSearch = TrackSearch.create(TrackManager.KAVITA).also {
        it.title = name
        it.summary = ""
    }
}

@Serializable
data class VolumeDto(
    val id: Int,
    val number: Int,
    val name: String,
    val pages: Int,
    val pagesRead: Int,
    val lastModified: String,
    val created: String,
    val seriesId: Int,
    val chapters: List<ChapterDto> = emptyList(),
)

@Serializable
data class ChapterDto(
    val id: Int? = -1,
    val range: String? = "",
    val number: String? = "-1",
    val pages: Int? = 0,
    val isSpecial: Boolean? = false,
    val title: String? = "",
    val pagesRead: Int? = 0,
    val coverImageLocked: Boolean? = false,
    val volumeId: Int? = -1,
    val created: String? = "",
)

@Serializable
data class AuthenticationDto(
    val username: String,
    val token: String,
    val apiKey: String,
)

data class SourceAuth(
    var sourceId: Int,
    var apiUrl: String = "",
    var jwtToken: String = "",
)
