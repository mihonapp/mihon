package eu.kanade.tachiyomi.data.recommendation

import java.util.Random
import kotlin.math.exp
import kotlin.math.ln

/** Stable quality-weighted sampling with MMR diversity and exposure-aware refill. */
internal object RecommendationSampler {
    private const val MINIMUM_SCORE = 0.20
    private const val TEMPERATURE = 0.15
    private const val MMR_LAMBDA = 0.85
    private const val MIN_RANDOM_UNIT = 1.0e-12

    fun sample(
        candidates: List<RankedRecommendation>,
        maxResults: Int,
        seed: Long,
        excludedKeys: Set<String> = emptySet(),
        exposureSnapshot: RecommendationExposureSnapshot = RecommendationExposureSnapshot.EMPTY,
    ): List<RecommendationCard> {
        if (maxResults <= 0) return emptyList()
        val eligible = mutableListOf<RankedRecommendation>()
        candidates.asSequence()
            .filter { it.score >= MINIMUM_SCORE }
            .filterNot { candidate -> candidate.card.identity.identityKeys().any(excludedKeys::contains) }
            .forEach { candidate ->
                if (eligible.none { RecommendationMetadata.sameWork(it.card.identity, candidate.card.identity) }) {
                    eligible += candidate
                }
            }
        if (eligible.isEmpty()) return emptyList()

        val random = Random(seed)
        val priorities = eligible.associate { candidate ->
            candidate.card.identity.exposureKey to random.nextDouble()
                .coerceIn(MIN_RANDOM_UNIT, 1.0 - MIN_RANDOM_UNIT)
        }
        val selected = mutableListOf<RankedRecommendation>()
        val unseen = eligible.filterNot { exposureSnapshot.wasShown(it.card.identity.identityKeys()) }
        selectWeighted(unseen, selected, maxResults, priorities)
        if (selected.size < maxResults) {
            val selectedSet = selected.toSet()
            eligible.asSequence()
                .filterNot(selectedSet::contains)
                .mapNotNull { candidate ->
                    exposureSnapshot.lastShown(candidate.card.identity.identityKeys())?.let { it to candidate }
                }
                .sortedWith(
                    compareBy<Pair<RecommendationExposure, RankedRecommendation>> { it.first.shownAtMillis }
                        .thenBy { it.first.sequence }
                        .thenByDescending { it.second.score },
                )
                .take(maxResults - selected.size)
                .mapTo(selected, Pair<RecommendationExposure, RankedRecommendation>::second)
        }
        return selected.map(RankedRecommendation::card)
    }

    private fun selectWeighted(
        candidates: List<RankedRecommendation>,
        selected: MutableList<RankedRecommendation>,
        maxResults: Int,
        priorities: Map<String, Double>,
    ) {
        val remaining = candidates.toMutableList()
        while (remaining.isNotEmpty() && selected.size < maxResults) {
            val utilities = remaining.associateWith { candidate ->
                val redundancy = selected.maxOfOrNull { chosen -> jaccard(candidate.tags, chosen.tags) } ?: 0.0
                MMR_LAMBDA * candidate.score - (1.0 - MMR_LAMBDA) * redundancy
            }
            val bestUtility = utilities.values.maxOrNull() ?: break
            val next = remaining.minByOrNull { candidate ->
                val weight = exp((utilities.getValue(candidate) - bestUtility) / TEMPERATURE)
                val uniform = priorities.getValue(candidate.card.identity.exposureKey)
                -ln(uniform) / weight.coerceAtLeast(MIN_RANDOM_UNIT)
            } ?: break
            selected += next
            remaining -= next
        }
    }

    private fun jaccard(left: Set<String>, right: Set<String>): Double {
        val union = left union right
        if (union.isEmpty()) return 0.0
        return (left intersect right).size.toDouble() / union.size.toDouble()
    }

    private fun RecommendationIdentity.identityKeys(): Set<String> = exposureKeys.ifEmpty { setOf(exposureKey) }
}
