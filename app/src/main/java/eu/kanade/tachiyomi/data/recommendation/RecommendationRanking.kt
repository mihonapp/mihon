package eu.kanade.tachiyomi.data.recommendation

import kotlin.math.ln

internal object RecommendationRanking {
    private const val RRF_K = 60.0
    private const val DEFAULT_MINIMUM_SCORE = 0.20
    private const val RARE_TAG_MAX_SHARE = 0.10

    fun buildTagProfile(
        targetTags: Collection<String>,
        documentFrequency: Map<String, Int>,
        documentCount: Int,
        routeIdentities: List<TagIdentity> = targetTags.map { TagIdentity(it, RecommendationMetadata.normalize(it)) },
    ): TagProfile {
        require(documentCount >= 0)
        val normalizedFrequencies = documentFrequency.entries.associate { (tag, count) ->
            RecommendationMetadata.normalize(tag) to count.coerceAtLeast(0)
        }
        val allTags = targetTags.asSequence()
            .map(RecommendationMetadata::normalize)
            .filter(String::isNotEmpty)
            .distinct()
            .toCollection(linkedSetOf())
        val originalOrder = allTags.withIndex().associate { it.value to it.index }
        val coreTags = allTags.sortedWith(
            compareByDescending<String> { tagWeight(it, normalizedFrequencies, documentCount) }
                .thenBy { originalOrder.getValue(it) },
        )
            .take(TagProfile.MAX_CORE_TAGS)
            .toCollection(linkedSetOf())
        val secondaryTags = allTags.filterNotTo(linkedSetOf(), coreTags::contains)
        val routesByTag = routeIdentities.asSequence()
            .map { TagIdentity(it.displayName, RecommendationMetadata.normalize(it.normalizedName)) }
            .filter { it.normalizedName in coreTags }
            .distinctBy(TagIdentity::normalizedName)
            .associateBy(TagIdentity::normalizedName)
        val routes = coreTags.mapNotNull(routesByTag::get)
        return TagProfile(allTags, coreTags, secondaryTags, routes)
    }

    fun rankSimilar(
        profile: TagProfile,
        candidates: Collection<RecommendationCandidate>,
        documentFrequency: Map<String, Int>,
        documentCount: Int,
        minimumScore: Double = DEFAULT_MINIMUM_SCORE,
    ): List<RankedRecommendation> {
        require(documentCount >= 0)
        require(minimumScore in 0.0..1.0)
        val normalizedFrequencies = documentFrequency.entries.associate { (tag, count) ->
            RecommendationMetadata.normalize(tag) to count.coerceAtLeast(0)
        }
        val distinct = mutableListOf<RecommendationCandidate>()
        candidates.forEach { candidate ->
            val duplicateIndex = distinct.indexOfFirst {
                RecommendationMetadata.sameWork(it.card.identity, candidate.card.identity)
            }
            if (duplicateIndex < 0) {
                distinct += candidate
            } else {
                val existing = distinct[duplicateIndex]
                distinct[duplicateIndex] = existing.copy(evidence = existing.evidence.merge(candidate.evidence))
            }
        }
        return distinct.mapNotNull { candidate ->
            val tags = candidate.card.tags
            if (!isReliable(profile, tags, candidate.evidence, normalizedFrequencies, documentCount)) {
                return@mapNotNull null
            }
            val contentScore = contentScore(profile, tags, normalizedFrequencies, documentCount)
            val rrf = normalizedRrf(candidate.evidence)
            val score = 0.60 * contentScore + 0.40 * rrf
            score.takeIf { it >= minimumScore }?.let {
                RankedRecommendation(candidate.card, tags, candidate.evidence, contentScore, score)
            }
        }.sortedWith(
            compareByDescending<RankedRecommendation>(RankedRecommendation::score)
                .thenBy { it.card.identity.exposureKey },
        )
    }

    fun weightedCoverage(
        targetTags: Set<String>,
        candidateTags: Set<String>,
        documentFrequency: Map<String, Int>,
        documentCount: Int,
    ): Double {
        if (targetTags.isEmpty()) return 0.0
        val total = targetTags.sumOf { tagWeight(it, documentFrequency, documentCount) }
        if (total <= 0.0) return 0.0
        val matched = (targetTags intersect candidateTags).sumOf {
            tagWeight(it, documentFrequency, documentCount)
        }
        return (matched / total).coerceIn(0.0, 1.0)
    }

    fun weightedJaccard(
        left: Set<String>,
        right: Set<String>,
        documentFrequency: Map<String, Int>,
        documentCount: Int,
    ): Double {
        val union = left union right
        if (union.isEmpty()) return 0.0
        val intersectionWeight = (left intersect right).sumOf {
            tagWeight(it, documentFrequency, documentCount)
        }
        val unionWeight = union.sumOf { tagWeight(it, documentFrequency, documentCount) }
        return if (unionWeight <= 0.0) 0.0 else (intersectionWeight / unionWeight).coerceIn(0.0, 1.0)
    }

    fun contentScore(
        profile: TagProfile,
        candidateTags: Set<String>,
        documentFrequency: Map<String, Int>,
        documentCount: Int,
    ): Double {
        val coverage = weightedCoverage(profile.coreTags, candidateTags, documentFrequency, documentCount)
        val jaccard = weightedJaccard(profile.coreTags, candidateTags, documentFrequency, documentCount)
        val secondaryBonus = weightedCoverage(
            profile.secondaryTags,
            candidateTags,
            documentFrequency,
            documentCount,
        )
        return (0.70 * coverage + 0.20 * jaccard + 0.10 * secondaryBonus).coerceIn(0.0, 1.0)
    }

    fun normalizedRrf(evidence: RecommendationEvidence): Double {
        if (evidence.ranks.isEmpty()) return 0.0
        val actual = evidence.ranks.entries.sumOf { (route, rank) -> route.weight / (RRF_K + rank + 1.0) }
        val ideal = evidence.ranks.keys.sumOf { route -> route.weight / (RRF_K + 1.0) }
        return if (ideal <= 0.0) 0.0 else (actual / ideal).coerceIn(0.0, 1.0)
    }

    private fun isReliable(
        profile: TagProfile,
        candidateTags: Set<String>,
        evidence: RecommendationEvidence,
        documentFrequency: Map<String, Int>,
        documentCount: Int,
    ): Boolean {
        if (evidence.authoritative) {
            val isStructuredFilter = RecommendationRoute.SOURCE_FILTER in evidence.ranks
            if (!isStructuredFilter || candidateTags.isEmpty()) return true
            return (profile.allTags intersect candidateTags).isNotEmpty()
        }
        if (profile.coreTags.isEmpty() || candidateTags.isEmpty()) return false
        val shared = profile.coreTags intersect candidateTags
        if (shared.size >= 2) return true
        if (profile.coreTags.size == 1 && shared.size == 1) return true
        if (shared.size != 1 || documentCount <= 0) return false
        val tag = shared.single()
        val share = (documentFrequency[tag] ?: documentCount).toDouble() / documentCount.toDouble()
        val coverage = weightedCoverage(profile.coreTags, candidateTags, documentFrequency, documentCount)
        return share <= RARE_TAG_MAX_SHARE && coverage >= 0.25
    }

    private fun tagWeight(
        tag: String,
        documentFrequency: Map<String, Int>,
        documentCount: Int,
    ): Double {
        if (documentCount <= 0) return 1.0
        val frequency = (documentFrequency[tag] ?: 0).coerceIn(0, documentCount)
        return ln((documentCount + 1.0) / (frequency + 1.0)) + 1.0
    }
}

internal object RecommendationCreators {
    fun selectWorks(
        target: RecommendationCard,
        candidates: Collection<RecommendationCard>,
        excluded: Collection<RecommendationIdentity> = emptyList(),
        maxResults: Int = 10,
    ): List<RecommendationCard> {
        if (maxResults <= 0 || target.creators.isEmpty()) return emptyList()
        return candidates.asSequence()
            .filter { it.sourceId == target.sourceId }
            .filter { RecommendationMetadata.creatorsOverlap(target.creators, it.creators) }
            .filterNot { RecommendationMetadata.sameWork(target.identity, it.identity) }
            .filterNot { candidate -> excluded.any { RecommendationMetadata.sameWork(candidate.identity, it) } }
            .distinctBy { it.identity.exposureKey }
            .sortedWith(
                compareByDescending<RecommendationCard> { it.favorite }
                    .thenBy { it.identity.exactTitle }
                    .thenBy { it.identity.exposureKey },
            )
            .take(maxResults)
            .toList()
    }
}
