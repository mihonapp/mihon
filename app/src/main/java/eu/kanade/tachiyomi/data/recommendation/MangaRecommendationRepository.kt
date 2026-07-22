package eu.kanade.tachiyomi.data.recommendation

import eu.kanade.domain.manga.model.toSManga
import eu.kanade.tachiyomi.data.track.anilist.dto.ALRecommendation
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.isLocalOrStub
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.model.Manga
import java.security.SecureRandom

/**
 * Builds conservative recommendations from source-scoped local metadata and, when explicitly
 * enabled, a small number of recommendation-only source requests.
 */
internal class MangaRecommendationRepository(
    private val localCandidateLoader: suspend (sourceId: Long, excludedUrl: String) -> List<Manga>,
    private val aniListLoader: suspend (mediaId: Long) -> List<ALRecommendation>,
    private val requestScheduler: RecommendationRequestScheduler = RecommendationRequestScheduler(),
    private val exposureStore: RecommendationExposureStore = RecommendationExposureStore(),
    private val seedProvider: () -> Long = SecureRandom()::nextLong,
) {

    fun observe(
        source: Source,
        manga: SManga,
        aniListId: Long?,
        allowNetwork: Boolean,
        excludedKeys: Set<String> = emptySet(),
    ): Flow<RecommendationRows> = flow {
        val target = RecommendationMetadata.card(source.id, manga.copy())
        if (manga.url.isBlank() || source.isLocalOrStub()) {
            emit(RecommendationRows())
            return@flow
        }

        val localCards = loadLocalCards(source.id, manga.url)
            .filterNot { RecommendationMetadata.sameWork(target.identity, it.identity) }
            .filterNot { it.identity.exposureKeys.any(excludedKeys::contains) }
        val documentFrequency = documentFrequency(localCards)
        val targetTags = RecommendationMetadata.extractTagIdentities(manga)
        val profile = RecommendationRanking.buildTagProfile(
            targetTags = targetTags.map(TagIdentity::normalizedName),
            documentFrequency = documentFrequency,
            documentCount = localCards.size,
            routeIdentities = targetTags,
        )
        val exposureSnapshot = exposureStore.snapshot(source.id, target.identity.exposureKey)
        val seed = seedProvider()

        val creatorWorks = RecommendationCreators.selectWorks(
            target = target,
            candidates = localCards,
            maxResults = MAX_RESULTS,
        ).toMutableList()
        val creatorKeys = creatorWorks.flatMapTo(linkedSetOf()) { it.identity.exposureKeys }
        val localSimilar = localCards.mapIndexed { index, card ->
            RecommendationCandidate(
                card = card,
                evidence = RecommendationEvidence(mapOf(RecommendationRoute.LOCAL to index)),
            )
        }
        val similarManga = RecommendationSampler.sample(
            candidates = RecommendationRanking.rankSimilar(
                profile = profile,
                candidates = localSimilar,
                documentFrequency = documentFrequency,
                documentCount = localCards.size,
            ),
            maxResults = MAX_RESULTS,
            seed = seed,
            excludedKeys = excludedKeys + creatorKeys,
            exposureSnapshot = exposureSnapshot,
        ).toMutableList()

        var rows = RecommendationRows(creatorWorks.toList(), similarManga.toList())
        emit(rows)
        if (!allowNetwork) {
            exposureStore.record(source.id, target.identity.exposureKey, rows.allCards())
            return@flow
        }

        val budget = SourceRequestBudget(source.id, requestScheduler)
        var detailRequests = 0

        suspend fun publishCreator(card: RecommendationCard) {
            if (creatorWorks.size >= MAX_RESULTS || !isNewCard(card, target, rows, excludedKeys)) return
            creatorWorks += card
            rows = rows.copy(creatorWorks = creatorWorks.toList())
            emit(rows)
        }

        suspend fun publishSimilar(card: RecommendationCard) {
            if (similarManga.size >= MAX_RESULTS || !isNewCard(card, target, rows, excludedKeys)) return
            similarManga += card
            rows = rows.copy(similarManga = similarManga.toList())
            emit(rows)
        }

        val strongestCreator = target.creators.sortedWith(
            compareBy<CreatorIdentity> { creator -> creator.roles.minOfOrNull(CreatorRole::ordinal) ?: Int.MAX_VALUE }
                .thenBy(CreatorIdentity::normalizedName),
        ).firstOrNull()
        if (creatorWorks.size < MAX_RESULTS && strongestCreator != null) {
            val results = budget.call {
                source.getSearchManga(1, strongestCreator.displayName, freshFilters(source)).mangas
            }.orEmpty().take(MAX_ROUTE_RESULTS)
            for (candidate in results) {
                var card = mergeLocalMetadata(
                    RecommendationMetadata.card(source.id, candidate.copy()),
                    localCards,
                )
                if (card.creators.isEmpty() && detailRequests < MAX_DETAIL_REQUESTS) {
                    detailRequests += 1
                    val hydrated = budget.call { loadDetails(source, candidate) }
                    if (hydrated != null) {
                        card = mergeLocalMetadata(RecommendationMetadata.card(source.id, hydrated), localCards)
                    }
                }
                if (RecommendationMetadata.creatorsOverlap(target.creators, card.creators)) {
                    publishCreator(card)
                }
                if (creatorWorks.size >= MAX_RESULTS || budget.stopped) break
            }
        }

        if (similarManga.size < MAX_RESULTS && !budget.stopped) {
            if (aniListId != null && aniListId > 0L) {
                val recommendations = loadAniList(aniListId).take(MAX_ANILIST_MAPPINGS)
                recommendations.forEachIndexed { index, recommendation ->
                    if (similarManga.size >= MAX_RESULTS || budget.stopped) return@forEachIndexed
                    val variants = recommendation.media.title.variants(recommendation.media.synonyms)
                        .filter(String::isNotBlank)
                    val query = variants.firstOrNull() ?: return@forEachIndexed
                    val matches = budget.call {
                        source.getSearchManga(1, query, freshFilters(source)).mangas
                    }.orEmpty()
                        .filter { result ->
                            val normalizedTitle = RecommendationMetadata.normalize(result.title)
                            variants.any { RecommendationMetadata.normalize(it) == normalizedTitle }
                        }
                        .map { mergeLocalMetadata(RecommendationMetadata.card(source.id, it.copy()), localCards) }
                        .distinctWorks()
                    if (matches.size == 1) {
                        val candidate = RecommendationCandidate(
                            card = matches.single(),
                            evidence = RecommendationEvidence(
                                ranks = mapOf(RecommendationRoute.ANILIST to index),
                                authoritative = true,
                            ),
                        )
                        val ranked = RecommendationRanking.rankSimilar(
                            profile = profile,
                            candidates = listOf(candidate),
                            documentFrequency = documentFrequency,
                            documentCount = localCards.size,
                        )
                        ranked.singleOrNull()?.card?.let { publishSimilar(it) }
                    }
                }
            } else {
                val route = profile.routeIdentities.firstOrNull()
                if (route != null) {
                    val filters = freshFilters(source)
                    val structured = RecommendationMetadata.applyExactTagFilter(filters, route.displayName)
                    val results = budget.call {
                        source.getSearchManga(
                            page = 1,
                            query = if (structured) "" else route.displayName,
                            filters = filters,
                        ).mangas
                    }.orEmpty().take(MAX_ROUTE_RESULTS)
                    val candidates = mutableListOf<RecommendationCandidate>()
                    for ((index, candidate) in results.withIndex()) {
                        var card = mergeLocalMetadata(
                            RecommendationMetadata.card(source.id, candidate.copy()),
                            localCards,
                        )
                        if (!structured && card.tags.isEmpty() && detailRequests < MAX_DETAIL_REQUESTS) {
                            detailRequests += 1
                            val hydrated = budget.call { loadDetails(source, candidate) }
                            if (hydrated != null) {
                                card = mergeLocalMetadata(RecommendationMetadata.card(source.id, hydrated), localCards)
                            }
                        }
                        val recommendationRoute = if (structured) {
                            RecommendationRoute.SOURCE_FILTER
                        } else {
                            RecommendationRoute.SOURCE_SEARCH
                        }
                        candidates += RecommendationCandidate(
                            card = card,
                            evidence = RecommendationEvidence(
                                ranks = mapOf(recommendationRoute to index),
                                authoritative = structured,
                            ),
                        )
                        if (budget.stopped) break
                    }
                    val ranked = RecommendationRanking.rankSimilar(
                        profile = profile,
                        candidates = candidates,
                        documentFrequency = documentFrequency,
                        documentCount = localCards.size,
                    )
                    RecommendationSampler.sample(
                        candidates = ranked,
                        maxResults = MAX_RESULTS - similarManga.size,
                        seed = seed xor NETWORK_SEED_SALT,
                        excludedKeys = excludedKeys + rows.allCards().flatMap { it.identity.exposureKeys },
                        exposureSnapshot = exposureSnapshot,
                    ).forEach { publishSimilar(it) }
                }
            }
        }

        exposureStore.record(source.id, target.identity.exposureKey, rows.allCards())
    }

    fun invalidate(sourceId: Long, targetKey: String) {
        exposureStore.clear(sourceId, targetKey)
        requestScheduler.clear(sourceId)
    }

    private suspend fun loadLocalCards(sourceId: Long, excludedUrl: String): List<RecommendationCard> {
        return try {
            localCandidateLoader(sourceId, excludedUrl)
                .asSequence()
                .filter { it.source == sourceId && it.initialized }
                .take(MAX_LOCAL_CANDIDATES)
                .map { local ->
                    RecommendationMetadata.card(
                        sourceId = sourceId,
                        manga = local.toSManga(),
                        favorite = local.favorite,
                        localId = local.id,
                    )
                }
                .toList()
                .distinctWorks()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            logcat(LogPriority.DEBUG, error) { "Unable to load local recommendation candidates" }
            emptyList()
        }
    }

    private suspend fun loadAniList(mediaId: Long): List<ALRecommendation> {
        return try {
            aniListLoader(mediaId)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            logcat(LogPriority.DEBUG, error) { "Unable to load AniList recommendations" }
            emptyList()
        }
    }

    private suspend fun loadDetails(source: Source, identity: SManga): SManga {
        val details = source.getMangaUpdate(identity, emptyList(), fetchDetails = true, fetchChapters = false).manga
        return identity.copy().apply {
            details.author?.takeIf(String::isNotBlank)?.let { author = it }
            details.artist?.takeIf(String::isNotBlank)?.let { artist = it }
            details.description?.takeIf(String::isNotBlank)?.let { description = it }
            details.genre?.takeIf(String::isNotBlank)?.let { genre = it }
            details.thumbnail_url?.takeIf(String::isNotBlank)?.let { thumbnail_url = it }
            initialized = initialized || details.initialized
        }
    }

    private fun mergeLocalMetadata(
        network: RecommendationCard,
        localCards: List<RecommendationCard>,
    ): RecommendationCard {
        val local = localCards.firstOrNull {
            RecommendationMetadata.sameWork(network.identity, it.identity)
        } ?: return network
        val merged = network.manga.copy().apply {
            if (author.isNullOrBlank()) author = local.manga.author
            if (artist.isNullOrBlank()) artist = local.manga.artist
            if (description.isNullOrBlank()) description = local.manga.description
            if (genre.isNullOrBlank()) genre = local.manga.genre
            if (thumbnail_url.isNullOrBlank()) thumbnail_url = local.manga.thumbnail_url
            initialized = initialized || local.manga.initialized
        }
        return RecommendationMetadata.card(
            sourceId = network.sourceId,
            manga = merged,
            favorite = local.favorite,
            localId = local.localId,
        )
    }

    private fun documentFrequency(cards: List<RecommendationCard>): Map<String, Int> {
        val frequencies = mutableMapOf<String, Int>()
        cards.forEach { card ->
            card.tags.forEach { tag -> frequencies[tag] = (frequencies[tag] ?: 0) + 1 }
        }
        return frequencies
    }

    private fun isNewCard(
        candidate: RecommendationCard,
        target: RecommendationCard,
        rows: RecommendationRows,
        excludedKeys: Set<String>,
    ): Boolean {
        if (candidate.sourceId != target.sourceId) return false
        if (candidate.identity.exposureKeys.any(excludedKeys::contains)) return false
        if (RecommendationMetadata.sameWork(candidate.identity, target.identity)) return false
        return rows.allCards().none {
            RecommendationMetadata.sameWork(candidate.identity, it.identity)
        }
    }

    private fun List<RecommendationCard>.distinctWorks(): List<RecommendationCard> {
        val result = mutableListOf<RecommendationCard>()
        forEach { card ->
            if (result.none { RecommendationMetadata.sameWork(it.identity, card.identity) }) result += card
        }
        return result
    }

    private fun freshFilters(source: Source): FilterList {
        return runCatching(source::getFilterList).getOrElse { FilterList() }
    }

    private class SourceRequestBudget(
        private val sourceId: Long,
        private val scheduler: RecommendationRequestScheduler,
    ) {
        var stopped = false
            private set
        private var requests = 0

        suspend fun <T> call(block: suspend () -> T): T? {
            if (stopped || requests >= MAX_SOURCE_REQUESTS) return null
            requests += 1
            return try {
                when (val result = scheduler.execute(sourceId, block)) {
                    is RecommendationRequestResult.Success -> result.value
                    is RecommendationRequestResult.RateLimited -> {
                        stopped = true
                        null
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                logcat(LogPriority.DEBUG, error) { "Recommendation source request failed" }
                null
            }
        }
    }

    private companion object {
        const val MAX_LOCAL_CANDIDATES = 200
        const val MAX_RESULTS = 10
        const val MAX_ROUTE_RESULTS = 12
        const val MAX_SOURCE_REQUESTS = 4
        const val MAX_DETAIL_REQUESTS = 2
        const val MAX_ANILIST_MAPPINGS = 2
        const val NETWORK_SEED_SALT = 0x2A72B17C4D9E3051L
    }
}

private fun RecommendationRows.allCards(): List<RecommendationCard> = creatorWorks + similarManga
