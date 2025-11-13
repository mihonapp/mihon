package tachiyomi.data.release

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Contains information about the latest release from GitHub.
 */
@Serializable
data class GithubRelease(
    @SerialName("tag_name")
    val version: String,
    @SerialName("body")
    val info: String,
    @SerialName("html_url")
    val releaseLink: String,
    @SerialName("assets")
    val assets: List<GitHubAsset>,
)

/**
 * Asset class containing asset name and download url.
 */
@Serializable
data class GitHubAsset(
    val name: String,
    @SerialName("browser_download_url")
    val downloadLink: String,
)
