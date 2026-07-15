package eu.kanade.tachiyomi.data.track.anilist.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val MAX_RECOMMENDATIONS = 4

@Serializable
data class ALRecommendationsResult(
    val data: ALRecommendationsData,
) {
    fun recommendations(): List<ALRecommendation> {
        return data.media
            ?.recommendations
            ?.edges
            .orEmpty()
            .mapNotNull { edge ->
                edge.node.mediaRecommendation
                    ?.takeIf { it.type == "MANGA" }
                    ?.let { ALRecommendation(rating = edge.node.rating, media = it) }
            }
            .take(MAX_RECOMMENDATIONS)
    }
}

@Serializable
data class ALRecommendationsData(
    @SerialName("Media")
    val media: ALRecommendationSourceMedia? = null,
)

@Serializable
data class ALRecommendationSourceMedia(
    val recommendations: ALRecommendationsConnection? = null,
)

@Serializable
data class ALRecommendationsConnection(
    val edges: List<ALRecommendationEdge> = emptyList(),
)

@Serializable
data class ALRecommendationEdge(
    val node: ALRecommendationNode,
)

@Serializable
data class ALRecommendationNode(
    val rating: Int = 0,
    val mediaRecommendation: ALRecommendationMedia? = null,
)

data class ALRecommendation(
    val rating: Int,
    val media: ALRecommendationMedia,
)

@Serializable
data class ALRecommendationMedia(
    val id: Long,
    val type: String,
    val title: ALRecommendationTitle,
    val synonyms: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val tags: List<ALRecommendationTag> = emptyList(),
)

@Serializable
data class ALRecommendationTitle(
    val userPreferred: String? = null,
    val romaji: String? = null,
    val english: String? = null,
    val native: String? = null,
) {
    fun variants(synonyms: List<String> = emptyList()): List<String> {
        return listOfNotNull(userPreferred, romaji, english, native)
            .plus(synonyms)
            .filter(String::isNotBlank)
            .distinct()
    }
}

@Serializable
data class ALRecommendationTag(
    val name: String,
    val rank: Int = 0,
)
