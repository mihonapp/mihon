package eu.kanade.tachiyomi.data.recommendation

import eu.kanade.tachiyomi.source.model.SManga

internal data class RecommendationRows(
    val creatorWorks: List<SManga> = emptyList(),
    val similarManga: List<SManga> = emptyList(),
    val creatorAuthoritative: Boolean = false,
    val similarAuthoritative: Boolean = false,
    /** Absolute wall-clock time at which an incomplete, recoverable lookup may be retried. */
    val retryAtMillis: Long? = null,
    /** True only for the terminal emission of one observation run. */
    val isFinal: Boolean = false,
)

internal enum class CreatorRole {
    AUTHOR,
    ARTIST,
    GROUP,
}

internal data class CreatorIdentity(
    val displayName: String,
    val normalizedName: String,
    val roles: Set<CreatorRole> = emptySet(),
)

internal enum class CreatorFilterKind {
    AUTHOR,
    ARTIST,
    GROUP,
}

internal data class CreatorFilterMatch(
    val role: CreatorRole,
    val kind: CreatorFilterKind,
    val filterName: String,
)

internal data class GenreIdentity(
    val displayName: String,
    val normalizedName: String,
)

/**
 * A conservative, source-scoped identity for a recommendation card.
 *
 * [canonicalUrl] remains the strongest key. The title/creator/series fields are deliberately
 * retained separately so callers can recognize the same work returned through alias gallery
 * URLs without collapsing unrelated works which merely have similar titles.
 */
internal data class RecommendationIdentity(
    val sourceId: Long,
    val canonicalUrl: String,
    /** Normalized host for absolute URLs, or null when the source returned a relative URL. */
    val urlHost: String?,
    /** Normalized path and identity query, independent of whether the URL was absolute. */
    val urlPath: String,
    val exactTitle: String,
    val baseTitle: String,
    val creators: Set<String>,
    val cover: String?,
    val series: Set<String>,
) {
    val exposureKeys: Set<String>
        get() = buildSet {
            if (canonicalUrl.isNotBlank()) add("$sourceId:url:$canonicalUrl")
            if (exactTitle.isNotBlank()) {
                creators.forEach { creator -> add("$sourceId:title:$exactTitle:$creator") }
            }
            if (baseTitle.isNotBlank() && creators.isNotEmpty()) {
                creators.forEach { creator ->
                    series.forEach { value -> add("$sourceId:base:$baseTitle:$creator:series:$value") }
                    cover?.let { value -> add("$sourceId:base:$baseTitle:$creator:cover:$value") }
                }
            }
        }

    val exposureKey: String
        get() = when {
            exactTitle.isNotBlank() && creators.isNotEmpty() -> {
                "$sourceId:title:$exactTitle:${creators.minOrNull()}"
            }
            baseTitle.isNotBlank() && creators.isNotEmpty() && series.isNotEmpty() -> {
                "$sourceId:base:$baseTitle:${creators.minOrNull()}:series:${series.minOrNull()}"
            }
            baseTitle.isNotBlank() && creators.isNotEmpty() && cover != null -> {
                "$sourceId:base:$baseTitle:${creators.minOrNull()}:cover:$cover"
            }
            canonicalUrl.isNotBlank() -> "$sourceId:url:$canonicalUrl"
            else -> "$sourceId:unknown"
        }
}

/** Target metadata separated into retrieval and scoring roles. */
internal data class TagProfile(
    val allTags: Set<String>,
    val coreTags: Set<String>,
    val secondaryTags: Set<String>,
    val routeIdentities: List<GenreIdentity>,
) {
    init {
        require(coreTags.size <= MAX_CORE_TAGS)
        require(coreTags.all(allTags::contains))
        require(secondaryTags.all(allTags::contains))
        require((coreTags intersect secondaryTags).isEmpty())
    }

    companion object {
        const val MAX_CORE_TAGS = 4
    }
}

internal data class CandidateEvidence(
    val aniListRank: Int? = null,
    val sourceRelatedRank: Int? = null,
    val genreSearchRank: Int? = null,
    val queryRank: Int? = null,
    val popularRank: Int? = null,
    val strongRouteGenres: Set<String> = emptySet(),
    val queriedGenres: Set<String> = emptySet(),
    val externalGenres: Set<String> = emptySet(),
) {
    val hasAniListEvidence: Boolean
        get() = aniListRank != null

    val hasSourceRelatedEvidence: Boolean
        get() = sourceRelatedRank != null

    val hasAuthoritativeEvidence: Boolean
        get() = hasAniListEvidence || hasSourceRelatedEvidence

    fun merge(other: CandidateEvidence): CandidateEvidence {
        return CandidateEvidence(
            aniListRank = minRank(aniListRank, other.aniListRank),
            sourceRelatedRank = minRank(sourceRelatedRank, other.sourceRelatedRank),
            genreSearchRank = minRank(genreSearchRank, other.genreSearchRank),
            queryRank = minRank(queryRank, other.queryRank),
            popularRank = minRank(popularRank, other.popularRank),
            strongRouteGenres = strongRouteGenres + other.strongRouteGenres,
            queriedGenres = queriedGenres + other.queriedGenres,
            externalGenres = externalGenres + other.externalGenres,
        )
    }

    private fun minRank(left: Int?, right: Int?): Int? = listOfNotNull(left, right).minOrNull()
}

internal data class SimilarCandidate(
    val manga: SManga,
    val evidence: CandidateEvidence,
)

internal data class RankedSimilarCandidate(
    val manga: SManga,
    val genres: Set<String>,
    val evidence: CandidateEvidence,
    val contentScore: Double,
    val score: Double,
)
