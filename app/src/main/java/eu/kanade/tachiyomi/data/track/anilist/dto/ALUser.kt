package eu.kanade.tachiyomi.data.track.anilist.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ALCurrentUserResult(
    val data: ALUserViewer,
)

@Serializable
data class ALUserViewer(
    @SerialName("Viewer")
    val viewer: ALUserViewerData,
)

@Serializable
data class ALUserViewerData(
    val id: Int,
    val mediaListOptions: ALUserListOptions,
)

@Serializable
data class ALUserListOptions(
    val scoreFormat: String,
)
