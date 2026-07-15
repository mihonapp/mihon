package eu.kanade.tachiyomi.data.recommendation

import eu.kanade.tachiyomi.source.model.SManga
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RecommendationRankingTest {

    @Test
    fun `document frequency counts a genre once per manga`() {
        val pool = listOf(
            manga("one", genres = "Fantasy, Fantasy, Action"),
            manga("two", genres = "Fantasy, Romance"),
            manga("three", genres = null),
        )

        assertEquals(
            mapOf("fantasy" to 2, "action" to 1, "romance" to 1),
            RecommendationRanking.documentFrequency(pool),
        )
    }

    @Test
    fun `IDF weighted Jaccard gives rare shared genres more weight`() {
        val target = setOf("common", "rare")
        val documentFrequency = mapOf("common" to 9, "rare" to 1, "other" to 10)

        val commonOverlap = RecommendationRanking.weightedJaccard(
            left = target,
            right = setOf("common", "other"),
            documentFrequency = documentFrequency,
            documentCount = 10,
        )
        val rareOverlap = RecommendationRanking.weightedJaccard(
            left = target,
            right = setOf("rare", "other"),
            documentFrequency = documentFrequency,
            documentCount = 10,
        )

        assertTrue(rareOverlap > commonOverlap)
        assertEquals(
            0.0,
            RecommendationRanking.weightedJaccard(
                left = target,
                right = setOf("unrelated"),
                documentFrequency = documentFrequency,
                documentCount = 10,
            ),
        )
    }

    @Test
    fun `single genre reliability requires coverage and rarity boundaries`() {
        val evidence = CandidateEvidence(queryRank = 0)
        val targetGenres = linkedSetOf("mystery", "romance", "school_life", "comedy")
        val candidateGenres = setOf("mystery", "candidate only")

        assertTrue(
            RecommendationRanking.isReliable(
                targetGenres,
                candidateGenres,
                evidence,
                documentFrequency = mapOf(
                    "mystery" to 1,
                    "romance" to 9,
                    "school_life" to 9,
                    "comedy" to 9,
                ),
                documentCount = 10,
                contentScore = 0.0,
            ),
        )
        assertFalse(
            RecommendationRanking.isReliable(
                targetGenres,
                candidateGenres,
                evidence,
                documentFrequency = mapOf(
                    "mystery" to 2,
                    "romance" to 9,
                    "school_life" to 9,
                    "comedy" to 9,
                ),
                documentCount = 10,
                contentScore = 1.0,
            ),
        )
    }

    @Test
    fun `two shared genres are reliable without external evidence`() {
        assertTrue(
            RecommendationRanking.isReliable(
                targetGenres = setOf("fantasy", "adventure"),
                candidateGenres = setOf("fantasy", "adventure"),
                evidence = CandidateEvidence(queryRank = 0),
                documentFrequency = emptyMap(),
                documentCount = 0,
                contentScore = 0.0,
            ),
        )
    }

    @Test
    fun `the only informative target genre can be verified without a local corpus`() {
        assertTrue(
            RecommendationRanking.isReliable(
                targetGenres = setOf("mystery"),
                candidateGenres = setOf("mystery", "drama"),
                evidence = CandidateEvidence(popularRank = 0),
                documentFrequency = emptyMap(),
                documentCount = 0,
                contentScore = 0.5,
            ),
        )
    }

    @Test
    fun `exact source genre route is reliable without local document frequency`() {
        assertTrue(
            RecommendationRanking.isReliable(
                targetGenres = setOf("mystery"),
                candidateGenres = setOf("mystery"),
                evidence = CandidateEvidence(
                    genreSearchRank = 0,
                    strongRouteGenres = setOf("mystery"),
                ),
                documentFrequency = emptyMap(),
                documentCount = 0,
                contentScore = 1.0,
            ),
        )
    }

    @Test
    fun `strong route evidence verifies a bare source card without faking content similarity`() {
        val candidate = manga("route-result", genres = null)

        val ranked = scoreMangaCandidates(
            targetGenres = setOf("mystery"),
            candidates = listOf(
                SimilarCandidate(
                    manga = candidate,
                    evidence = CandidateEvidence(
                        genreSearchRank = 0,
                        strongRouteGenres = setOf("mystery"),
                    ),
                ),
            ),
            documentFrequency = emptyMap(),
            documentCount = 0,
            maxResults = 10,
        )

        assertEquals(listOf(candidate), ranked)
    }

    @Test
    fun `route genre prioritizes semantic facet before local rarity`() {
        assertEquals(
            "mystery",
            RecommendationRanking.routeGenres(
                targetGenres = linkedSetOf("mystery", "school_life"),
                documentFrequency = emptyMap(),
            ).firstOrNull(),
        )
        assertEquals(
            "mystery",
            RecommendationRanking.routeGenres(
                targetGenres = linkedSetOf("mystery", "school_life"),
                documentFrequency = mapOf("mystery" to 0, "school_life" to 4),
            ).firstOrNull(),
        )
    }

    @Test
    fun `equal information routes keep deterministic source order without a target seed`() {
        assertEquals(
            listOf("tag-a", "tag-b", "tag-c", "tag-d"),
            RecommendationRanking.routeGenres(
                targetGenres = linkedSetOf("tag-a", "tag-b", "tag-c", "tag-d"),
                documentFrequency = emptyMap(),
            ),
        )
    }

    @Test
    fun `popular rank alone cannot make an unrelated candidate reliable`() {
        val result = scoreMangaCandidates(
            targetGenres = setOf("fantasy", "adventure"),
            candidates = listOf(
                SimilarCandidate(
                    manga = manga("popular", genres = null),
                    evidence = CandidateEvidence(popularRank = 0),
                ),
            ),
            documentFrequency = emptyMap(),
            documentCount = 10,
            maxResults = 10,
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `AniList evidence is reliable even when source genres are absent`() {
        val candidate = manga("anilist", genres = null)

        val result = scoreMangaCandidates(
            targetGenres = setOf("fantasy", "adventure"),
            candidates = listOf(
                SimilarCandidate(
                    manga = candidate,
                    evidence = CandidateEvidence(aniListRank = 0),
                ),
            ),
            documentFrequency = emptyMap(),
            documentCount = 10,
            maxResults = 10,
        )

        assertEquals(listOf(candidate), result)
    }

    @Test
    fun `RRF fusion scores are deterministic regardless of candidate input order`() {
        val targetGenres = setOf("a", "b", "c", "d")
        val documentFrequency = targetGenres.associateWith { 2 }
        val fused = SimilarCandidate(
            manga = manga("a-fused", genres = "a, b"),
            evidence = CandidateEvidence(
                aniListRank = 0,
                queryRank = 0,
                genreSearchRank = 0,
                popularRank = 0,
            ),
        )
        val redundant = SimilarCandidate(
            manga = manga("b-redundant", genres = "a, b"),
            evidence = CandidateEvidence(queryRank = 0),
        )
        val diverse = SimilarCandidate(
            manga = manga("c-diverse", genres = "c, d"),
            evidence = CandidateEvidence(queryRank = 0),
        )
        val candidates = listOf(redundant, diverse, fused)

        fun rank(input: List<SimilarCandidate>): List<String> {
            return scoreMangaCandidates(
                targetGenres = targetGenres,
                candidates = input,
                documentFrequency = documentFrequency,
                documentCount = 10,
                maxResults = 10,
            ).map(RecommendationMetadata::safeUrl)
        }

        assertEquals(
            listOf("a-fused", "b-redundant", "c-diverse"),
            rank(candidates),
        )
        assertEquals(rank(candidates), rank(candidates.reversed()))
    }

    @Test
    fun `equal ranked candidates use canonical URL as deterministic tie breaker`() {
        val first = SimilarCandidate(
            manga = manga("a-url", genres = "fantasy, adventure"),
            evidence = CandidateEvidence(queryRank = 0),
        )
        val second = SimilarCandidate(
            manga = manga("b-url", genres = "fantasy, adventure"),
            evidence = CandidateEvidence(queryRank = 0),
        )

        val ranked = scoreMangaCandidates(
            targetGenres = setOf("fantasy", "adventure"),
            candidates = listOf(second, first),
            documentFrequency = emptyMap(),
            documentCount = 10,
            maxResults = 10,
        )

        assertEquals(listOf("a-url", "b-url"), ranked.map(RecommendationMetadata::safeUrl))
    }

    @Test
    fun `candidate evidence merge keeps best ranks and unions external genres`() {
        val merged = CandidateEvidence(
            queryRank = 4,
            aniListRank = 2,
            strongRouteGenres = setOf("romance"),
            externalGenres = setOf("fantasy"),
        ).merge(
            CandidateEvidence(
                queryRank = 1,
                genreSearchRank = 3,
                popularRank = 5,
                strongRouteGenres = setOf("school_life"),
                externalGenres = setOf("adventure"),
            ),
        )

        assertEquals(
            CandidateEvidence(
                queryRank = 1,
                aniListRank = 2,
                genreSearchRank = 3,
                popularRank = 5,
                strongRouteGenres = setOf("romance", "school_life"),
                externalGenres = setOf("fantasy", "adventure"),
            ),
            merged,
        )
        assertTrue(merged.hasAniListEvidence)
        assertFalse(CandidateEvidence(popularRank = 0).hasAniListEvidence)
    }

    @Test
    fun `tag profile limits scoring core and keeps source native provisional routes`() {
        val identities = listOf(
            GenreIdentity("愛情", "romance"),
            GenreIdentity("校園", "school_life"),
            GenreIdentity("歡樂向", "comedy"),
            GenreIdentity("青年", "seinen"),
            GenreIdentity("Custom Native Tag", "custom_native_tag"),
        )
        val profile = RecommendationRanking.tagProfile(
            targetGenres = identities.mapTo(linkedSetOf(), GenreIdentity::normalizedName),
            targetGenreIdentities = identities,
            documentFrequency = mapOf(
                "romance" to 8,
                "school_life" to 5,
                "comedy" to 2,
                "seinen" to 3,
                "custom_native_tag" to 1,
            ),
            documentCount = 10,
        )

        assertEquals(
            listOf("custom_native_tag", "comedy", "school_life", "seinen"),
            profile.coreTags.toList(),
        )
        assertEquals(setOf("romance"), profile.secondaryTags)
        assertEquals(
            listOf("Custom Native Tag", "歡樂向", "校園", "青年"),
            profile.routeIdentities.map { it.displayName },
        )

        val provisionalOnly = RecommendationRanking.tagProfile(
            targetGenres = linkedSetOf("native_one", "native_two"),
            targetGenreIdentities = listOf(
                GenreIdentity("來源標籤一", "native_one"),
                GenreIdentity("來源標籤二", "native_two"),
            ),
            documentFrequency = emptyMap(),
            documentCount = 0,
        )
        assertTrue(provisionalOnly.coreTags.isEmpty())
        assertEquals(setOf("native_one", "native_two"), provisionalOnly.secondaryTags)
        assertEquals(listOf("來源標籤一", "來源標籤二"), provisionalOnly.routeIdentities.map { it.displayName })

        val mixed = RecommendationRanking.tagProfile(
            targetGenres = linkedSetOf("romance", "unseen_native"),
            targetGenreIdentities = listOf(
                GenreIdentity("Romance", "romance"),
                GenreIdentity("Unseen Native", "unseen_native"),
            ),
            documentFrequency = emptyMap(),
            documentCount = 0,
        )
        assertEquals(setOf("romance"), mixed.coreTags)
        assertEquals(setOf("unseen_native"), mixed.secondaryTags)
        assertEquals(listOf("Romance", "Unseen Native"), mixed.routeIdentities.map { it.displayName })
    }

    @Test
    fun `tag profile adapts core and secondary sets at zero one four eight and twenty tags`() {
        val expectedSizes = mapOf(
            0 to (0 to 0),
            1 to (1 to 0),
            4 to (4 to 0),
            8 to (4 to 4),
            20 to (4 to 16),
        )

        expectedSizes.forEach { (tagCount, expected) ->
            val tags = (1..tagCount).mapTo(linkedSetOf()) { "native_$it" }
            val profile = RecommendationRanking.tagProfile(
                targetGenres = tags,
                documentFrequency = tags.associateWith { 1 },
                documentCount = tagCount,
            )

            assertEquals(tagCount, profile.allTags.size, "all tags for count=$tagCount")
            assertEquals(expected.first, profile.coreTags.size, "core tags for count=$tagCount")
            assertEquals(expected.second, profile.secondaryTags.size, "secondary tags for count=$tagCount")
            assertEquals(tags.take(expected.first), profile.coreTags.toList(), "stable core order for count=$tagCount")
        }
    }

    @Test
    fun `content score uses seventy twenty ten weighting`() {
        val profile = TagProfile(
            allTags = setOf("core_a", "core_b", "secondary_a", "secondary_b"),
            coreTags = setOf("core_a", "core_b"),
            secondaryTags = setOf("secondary_a", "secondary_b"),
            routeIdentities = emptyList(),
        )
        val candidateTags = setOf("core_a", "secondary_a")

        val score = RecommendationRanking.contentScore(
            profile = profile,
            candidateTags = candidateTags,
            documentFrequency = profile.allTags.associateWith { 2 },
            documentCount = 4,
        )

        val expected = 0.70 * 0.5 + 0.20 * (1.0 / 3.0) + 0.10 * 0.5
        assertEquals(expected, score, 1.0e-12)
    }

    @Test
    fun `source native single tag remains usable without a local corpus`() {
        val profile = RecommendationRanking.tagProfile(
            targetGenres = setOf("來源自訂標籤"),
            targetGenreIdentities = listOf(GenreIdentity("來源自訂標籤", "來源自訂標籤")),
            documentFrequency = emptyMap(),
            documentCount = 0,
        )
        val result = scoreMangaCandidates(
            profile = profile,
            candidates = listOf(
                SimilarCandidate(
                    manga("verified-native", genres = null),
                    CandidateEvidence(
                        genreSearchRank = 0,
                        strongRouteGenres = setOf("來源自訂標籤"),
                    ),
                ),
            ),
            documentFrequency = emptyMap(),
            documentCount = 0,
            maxResults = 10,
        )

        assertTrue(profile.coreTags.isEmpty())
        assertEquals(setOf("來源自訂標籤"), profile.secondaryTags)
        assertEquals(listOf("verified-native"), result.map(RecommendationMetadata::safeUrl))
    }

    @Test
    fun `coverage remains high when a one tag target candidate has many extra tags`() {
        val profile = RecommendationRanking.tagProfile(
            targetGenres = setOf("romance"),
            documentFrequency = emptyMap(),
            documentCount = 0,
        )
        val candidateTags = setOf("romance") + (1..20).map { "extra_$it" }

        val coverage = RecommendationRanking.weightedCoverage(
            targetTags = profile.coreTags,
            candidateTags = candidateTags,
            documentFrequency = emptyMap(),
            documentCount = 0,
        )
        val score = RecommendationRanking.contentScore(
            profile = profile,
            candidateTags = candidateTags,
            documentFrequency = emptyMap(),
            documentCount = 0,
        )

        assertEquals(1.0, coverage)
        assertTrue(score >= 0.70)
    }

    @Test
    fun `many target tags retain four informative core tags without diluting reliability`() {
        val target = linkedSetOf(
            "romance",
            "mystery",
            "fantasy",
            "action",
            "school_life",
            "comedy",
            "shounen",
            "custom_extra",
        )
        val profile = RecommendationRanking.tagProfile(
            targetGenres = target,
            documentFrequency = emptyMap(),
            documentCount = 0,
        )
        val result = scoreMangaCandidates(
            profile = profile,
            candidates = listOf(
                SimilarCandidate(
                    manga("candidate", genres = profile.coreTags.take(2).joinToString(",")),
                    CandidateEvidence(queryRank = 0),
                ),
            ),
            documentFrequency = emptyMap(),
            documentCount = 0,
            maxResults = 10,
        )

        assertEquals(TagProfile.MAX_CORE_TAGS, profile.coreTags.size)
        assertEquals(listOf("candidate"), result.map(RecommendationMetadata::safeUrl))
    }

    @Test
    fun `zero tag targets require AniList or source related evidence`() {
        val candidates = listOf(
            SimilarCandidate(manga("popular", null), CandidateEvidence(popularRank = 0)),
            SimilarCandidate(manga("related", null), CandidateEvidence(sourceRelatedRank = 0)),
        )

        val ranked = scoreMangaCandidates(
            targetGenres = emptySet(),
            candidates = candidates,
            documentFrequency = emptyMap(),
            documentCount = 0,
            maxResults = 10,
        )

        assertEquals(listOf("related"), ranked.map(RecommendationMetadata::safeUrl))
    }

    @Test
    fun `strong route accepts bare cards but rejects declared conflicting details`() {
        val evidence = CandidateEvidence(
            genreSearchRank = 0,
            strongRouteGenres = setOf("romance"),
        )
        val ranked = scoreMangaCandidates(
            targetGenres = setOf("romance"),
            candidates = listOf(
                SimilarCandidate(manga("bare", null), evidence),
                SimilarCandidate(manga("conflict", "horror"), evidence),
            ),
            documentFrequency = emptyMap(),
            documentCount = 0,
            maxResults = 10,
        )

        assertEquals(listOf("bare"), ranked.map(RecommendationMetadata::safeUrl))
    }

    @Test
    fun `weak queried route must be verified by actual candidate tags`() {
        val evidence = CandidateEvidence(
            queryRank = 0,
            queriedGenres = setOf("romance"),
        )
        val ranked = scoreMangaCandidates(
            targetGenres = setOf("romance"),
            candidates = listOf(
                SimilarCandidate(manga("bare", null), evidence),
                SimilarCandidate(manga("verified", "romance, comedy"), evidence),
            ),
            documentFrequency = emptyMap(),
            documentCount = 0,
            maxResults = 10,
        )

        assertEquals(listOf("verified"), ranked.map(RecommendationMetadata::safeUrl))
    }

    @Test
    fun `one broad tag from popular is rejected but an exact query can verify it`() {
        val candidate = manga("romance", "romance")
        val popular = scoreMangaCandidates(
            targetGenres = setOf("romance"),
            candidates = listOf(SimilarCandidate(candidate, CandidateEvidence(popularRank = 0))),
            documentFrequency = mapOf("romance" to 9),
            documentCount = 10,
            maxResults = 10,
        )
        val queried = scoreMangaCandidates(
            targetGenres = setOf("romance"),
            candidates = listOf(
                SimilarCandidate(
                    candidate,
                    CandidateEvidence(queryRank = 0, queriedGenres = setOf("romance")),
                ),
            ),
            documentFrequency = mapOf("romance" to 9),
            documentCount = 10,
            maxResults = 10,
        )

        assertTrue(popular.isEmpty())
        assertEquals(listOf("romance"), queried.map(RecommendationMetadata::safeUrl))
    }

    @Test
    fun `tag rich target accepts one precise tag from a sparse source card`() {
        val target = linkedSetOf(
            "romance",
            "mystery",
            "fantasy",
            "action",
            "school_life",
            "comedy",
        )
        val ranked = scoreMangaCandidates(
            targetGenres = target,
            candidates = listOf(
                SimilarCandidate(manga("sparse", "mystery"), CandidateEvidence(queryRank = 0)),
            ),
            documentFrequency = emptyMap(),
            documentCount = 0,
            maxResults = 10,
        )

        assertEquals(listOf("sparse"), ranked.map(RecommendationMetadata::safeUrl))
    }

    @Test
    fun `unseen unknown residual tags cannot satisfy the two tag reliability gate`() {
        val ranked = scoreMangaCandidates(
            targetGenres = linkedSetOf("romance", "unknown_one", "unknown_two"),
            candidates = listOf(
                SimilarCandidate(
                    manga("noise-only", "unknown_one, unknown_two"),
                    CandidateEvidence(queryRank = 0),
                ),
            ),
            documentFrequency = emptyMap(),
            documentCount = 0,
            maxResults = 10,
        )

        assertTrue(ranked.isEmpty())
    }

    private fun scoreMangaCandidates(
        targetGenres: Set<String>,
        candidates: Collection<SimilarCandidate>,
        documentFrequency: Map<String, Int>,
        documentCount: Int,
        maxResults: Int,
    ): List<SManga> {
        return scoreMangaCandidates(
            profile = RecommendationRanking.tagProfile(
                targetGenres = targetGenres,
                documentFrequency = documentFrequency,
                documentCount = documentCount,
            ),
            candidates = candidates,
            documentFrequency = documentFrequency,
            documentCount = documentCount,
            maxResults = maxResults,
        )
    }

    private fun scoreMangaCandidates(
        profile: TagProfile,
        candidates: Collection<SimilarCandidate>,
        documentFrequency: Map<String, Int>,
        documentCount: Int,
        maxResults: Int,
    ): List<SManga> {
        return RecommendationRanking.scoreCandidates(
            profile = profile,
            candidates = candidates,
            documentFrequency = documentFrequency,
            documentCount = documentCount,
        )
            .take(maxResults)
            .map(RankedSimilarCandidate::manga)
    }

    private fun manga(url: String, genres: String?): SManga {
        return SManga.create().apply {
            this.url = url
            title = url
            genre = genres
            initialized = true
        }
    }
}
