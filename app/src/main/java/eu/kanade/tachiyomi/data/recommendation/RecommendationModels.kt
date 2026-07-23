package eu.kanade.tachiyomi.data.recommendation

import eu.kanade.tachiyomi.source.model.SManga

data class RecommendationRows(
    val creatorWorks: List<RecommendationCard> = emptyList(),
    val similarManga: List<RecommendationCard> = emptyList(),
)

data class RecommendationCard(
    val manga: SManga,
    val sourceId: Long,
    val identity: RecommendationIdentity,
    val creators: Set<CreatorIdentity> = emptySet(),
    val tags: Set<String> = emptySet(),
    val favorite: Boolean = false,
    val localId: Long? = null,
) {
    init {
        require(identity.sourceId == sourceId)
    }
}

enum class CreatorRole {
    AUTHOR,
    ARTIST,
    GROUP,
}

data class CreatorIdentity(
    val displayName: String,
    val normalizedName: String,
    val roles: Set<CreatorRole>,
)

/** A deliberately conservative identity which is always isolated by source ID. */
data class RecommendationIdentity(
    val sourceId: Long,
    val canonicalUrl: String,
    val urlHost: String?,
    val urlPathAndQuery: String,
    val exactTitle: String,
    val baseTitle: String,
    val creators: Set<String>,
    val cover: String?,
    val series: Set<String> = emptySet(),
) {
    val exposureKeys: Set<String>
        get() = buildSet {
            if (urlPathAndQuery.isNotBlank()) add("$sourceId:url:$canonicalUrl")
            if (exactTitle.isNotBlank()) {
                creators.forEach { add("$sourceId:title:$exactTitle:$it") }
            }
            if (baseTitle.isNotBlank()) {
                creators.forEach { creator ->
                    series.forEach { add("$sourceId:base:$baseTitle:$creator:series:$it") }
                    cover?.let { add("$sourceId:base:$baseTitle:$creator:cover:$it") }
                }
            }
        }

    val exposureKey: String
        get() = exposureKeys.minOrNull() ?: "$sourceId:unknown:$exactTitle"
}

internal data class TagIdentity(
    val displayName: String,
    val normalizedName: String,
)

internal data class TagProfile(
    val allTags: Set<String>,
    val coreTags: Set<String>,
    val secondaryTags: Set<String>,
    val routeIdentities: List<TagIdentity>,
) {
    init {
        require(coreTags.size <= MAX_CORE_TAGS)
        require(allTags.containsAll(coreTags))
        require(allTags.containsAll(secondaryTags))
        require((coreTags intersect secondaryTags).isEmpty())
    }

    internal companion object {
        const val MAX_CORE_TAGS = 4
    }
}

internal enum class RecommendationRoute(val weight: Double) {
    ANILIST(1.0),
    LOCAL(0.8),
    SOURCE_FILTER(0.8),
    SOURCE_SEARCH(0.6),
}

internal data class RecommendationEvidence(
    val ranks: Map<RecommendationRoute, Int>,
    val authoritative: Boolean = false,
) {
    init {
        require(ranks.values.all { it >= 0 })
    }

    fun merge(other: RecommendationEvidence): RecommendationEvidence {
        val merged = (ranks.keys + other.ranks.keys).associateWith { route ->
            minOf(ranks[route] ?: Int.MAX_VALUE, other.ranks[route] ?: Int.MAX_VALUE)
        }
        return RecommendationEvidence(merged, authoritative || other.authoritative)
    }
}

internal data class RecommendationCandidate(
    val card: RecommendationCard,
    val evidence: RecommendationEvidence,
)

internal data class RankedRecommendation(
    val card: RecommendationCard,
    val tags: Set<String>,
    val evidence: RecommendationEvidence,
    val contentScore: Double,
    val score: Double,
)
