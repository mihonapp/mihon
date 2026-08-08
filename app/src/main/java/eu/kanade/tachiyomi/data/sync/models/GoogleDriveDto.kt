package eu.kanade.tachiyomi.data.sync.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GoogleDriveOAuthResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long = 0,
)

@Serializable
data class GoogleDriveFileList(
    val files: List<GoogleDriveFile> = emptyList(),
)

@Serializable
data class GoogleDriveFile(
    val id: String,
    val name: String = "",
    val modifiedTime: String? = null,
)

@Serializable
data class GoogleDriveFileMetadata(
    val name: String,
    val parents: List<String>,
)
