package eu.kanade.tachiyomi.data.track.mangadex.dto

import eu.kanade.tachiyomi.data.track.mangadex.asUuidToLong
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

@Serializable
data class MangaDexOAuth(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_at") val expiresAt: Long,
) {
    fun isExpired() = Clock.System.now().plus(1.minutes).epochSeconds >= expiresAt
}

@Serializable
data class MangaDexTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("refresh_token") val refreshToken: String? = null,
) {
    fun toOauth(previousRefreshToken: String? = null) = MangaDexOAuth(
        accessToken = accessToken,
        refreshToken = refreshToken ?: previousRefreshToken.orEmpty(),
        expiresAt = Clock.System.now().epochSeconds + expiresIn,
    )
}

@Serializable
data class MangaDexUserResult(
    val data: MangaDexUser,
)

@Serializable
data class MangaDexUser(
    val id: String,
    val username: String,
)

@Serializable
data class MangaDexSearchResult(
    val data: List<MangaDexManga> = emptyList(),
    val limit: Int = 0,
    val offset: Int = 0,
    val total: Long = 0,
)

@Serializable
data class MangaDexMangaResult(
    val data: MangaDexManga,
)

@Serializable
data class MangaDexManga(
    val id: String,
    val attributes: MangaDexMangaAttributes,
    val relationships: List<MangaDexRelationship> = emptyList(),
) {
    fun toTrackSearch(trackerId: Long): TrackSearch {
        return TrackSearch.create(trackerId).apply {
            remote_id = this@MangaDexManga.id.asUuidToLong()
            title = this@MangaDexManga.attributes.getEnglishTitle()
            tracking_url = "https://mangadex.org/title/${this@MangaDexManga.id}"
            summary = attributes.getEnglishDescription()
            publishing_status = attributes.status
            publishing_type = attributes.originalLanguage.toPublishingType()
            start_date = attributes.year?.toString().orEmpty()
            score = -1.0
            total_chapters = attributes.lastChapter?.toFloatOrNull()?.toLong() ?: 0L
            cover_url = getCoverUrl().orEmpty()
        }
    }

    private fun getCoverUrl(): String? {
        val fileName = relationships
            .firstOrNull { it.type == "cover_art" }
            ?.attributes
            ?.let { it as? JsonObject }
            ?.get("fileName")
            ?.jsonPrimitive
            ?.content
            ?: return null
        return "https://uploads.mangadex.org/covers/$id/$fileName.512.jpg"
    }
}

@Serializable
data class MangaDexMangaAttributes(
    val title: Map<String, String> = emptyMap(),
    val description: Map<String, String> = emptyMap(),
    val year: Int? = null,
    val status: String = "",
    @SerialName("originalLanguage") val originalLanguage: String = "",
    @SerialName("lastChapter") val lastChapter: String? = null,
) {
    fun getEnglishTitle(): String = localizedValue(title) ?: "Unknown"

    fun getEnglishDescription(): String = localizedValue(description).orEmpty()

    private fun localizedValue(values: Map<String, String>): String? = values.entries
        .firstOrNull { it.key.equals("en", ignoreCase = true) || it.key.startsWith("en-") }
        ?.value
        ?: values.values.firstOrNull()
}

private fun String.toPublishingType(): String = when (this) {
    "ko" -> "Manhwa"
    "zh" -> "Manhua"
    else -> "Manga"
}

@Serializable
data class MangaDexRelationship(
    val id: String,
    val type: String,
    val attributes: JsonElement? = null,
)

@Serializable
data class MangaDexStatusResult(
    val status: String? = null,
)

@Serializable
data class MangaDexReadMarkersResult(
    val chapters: List<String> = emptyList(),
)

@Serializable
data class MangaDexChapterListResult(
    val data: List<MangaDexChapter> = emptyList(),
    val limit: Int = 0,
    val offset: Int = 0,
    val total: Long = 0,
)

@Serializable
data class MangaDexChapter(
    val id: String,
    val attributes: MangaDexChapterAttributes,
) {
    val number: Float
        get() = attributes.chapter.toFloatOrNull() ?: -1f
}

@Serializable
data class MangaDexChapterAttributes(
    val chapter: String = "",
)
