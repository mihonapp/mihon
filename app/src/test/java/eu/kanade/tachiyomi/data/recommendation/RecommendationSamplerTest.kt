package eu.kanade.tachiyomi.data.recommendation

import eu.kanade.tachiyomi.source.model.SManga
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RecommendationSamplerTest {

    @Test
    fun `a full unseen window is selected before recently shown work`() {
        val candidates = (1..20).map { ranked("work-$it", score = 0.8) }
        val recent = (1..10).map { "work-$it" }

        val result = sample(candidates, recent, seed = 7L)

        assertEquals((11..20).map { "work-$it" }.toSet(), result.map(SManga::url).toSet())
    }

    @Test
    fun `quality floor is applied before random sampling`() {
        val candidates = buildList {
            (1..10).forEach { add(ranked("good-$it", score = 0.8)) }
            (1..20).forEach { add(ranked("bad-$it", score = 0.19)) }
        }

        val result = sample(candidates, recent = emptyList(), seed = 11L)

        assertEquals(10, result.size)
        assertTrue(result.all { it.url.startsWith("good-") })
    }

    @Test
    fun `oldest previously shown works refill a sparse unseen pool`() {
        val candidates = buildList {
            (1..10).forEach { add(ranked("recent-$it", score = 0.8)) }
            (1..5).forEach { add(ranked("new-$it", score = 0.8)) }
        }
        val recent = (1..10).map { "recent-$it" }

        val result = sample(candidates, recent, seed = 19L).map(SManga::url)

        assertTrue(result.take(5).all { it.startsWith("new-") })
        assertEquals((1..5).map { "recent-$it" }, result.drop(5))
        assertEquals(10, result.size)
    }

    @Test
    fun `refill uses the real last shown time when target and source histories conflict`() {
        val candidates = listOf(
            ranked("shown-later-by-source", score = 0.9),
            ranked("shown-earlier-by-target", score = 0.8),
        )
        val targetExposure = timedSnapshot(
            "shown-later-by-source" to 1_000L,
            "shown-earlier-by-target" to 2_000L,
        )
        val sourceExposure = timedSnapshot(
            "source-filler-a" to 500L,
            "source-filler-b" to 2_500L,
            "shown-later-by-source" to 3_000L,
        )

        val result = RecommendationSampler.sample(
            candidates = candidates,
            maxResults = 2,
            unseenTargetSize = 2,
            maxPoolSize = 2,
            exposureSnapshot = targetExposure,
            sourceExposureSnapshot = sourceExposure,
            randomPriorities = mutableMapOf(),
            workKeys = { setOf(it.url) },
            nextRandomDouble = { error("All candidates have already been exposed") },
        )

        assertEquals(
            listOf("shown-earlier-by-target", "shown-later-by-source"),
            result.map(SManga::url),
        )
    }

    @Test
    fun `seeded quality sampling is reproducible without permanent anchors`() {
        val candidates = (1..30).map { ranked("work-$it", score = 0.75) }

        val first = sample(candidates, recent = emptyList(), seed = 91L).map(SManga::url)
        val again = sample(candidates, recent = emptyList(), seed = 91L).map(SManga::url)
        val differentSeed = sample(candidates, recent = emptyList(), seed = 92L).map(SManga::url)

        assertEquals(first, again)
        assertFalse(first == differentSeed)
        assertTrue(first.take(2) != listOf("work-1", "work-2"))
    }

    @Test
    fun `current sampler applies diversity after the first quality selection`() {
        val candidates = listOf(
            ranked("fused", score = 0.8, genres = setOf("a", "b")),
            ranked("redundant", score = 0.8, genres = setOf("a", "b")),
            ranked("diverse", score = 0.8, genres = setOf("c", "d")),
        )
        val priorities = mutableMapOf(
            "fused" to 0.99,
            "redundant" to 0.50,
            "diverse" to 0.50,
        )

        val result = RecommendationSampler.sample(
            candidates = candidates,
            maxResults = 3,
            unseenTargetSize = 3,
            maxPoolSize = 3,
            exposureSnapshot = RecommendationExposureSnapshot(emptyList()),
            randomPriorities = priorities,
            workKeys = { setOf(it.url) },
            nextRandomDouble = { error("Every priority is fixed by the test") },
        )

        assertEquals(listOf("fused", "diverse", "redundant"), result.map(SManga::url))
    }

    private fun sample(
        candidates: List<RankedSimilarCandidate>,
        recent: List<String>,
        seed: Long,
    ): List<SManga> {
        val random = java.util.Random(seed)
        return RecommendationSampler.sample(
            candidates = candidates,
            maxResults = 10,
            maxPoolSize = 40,
            exposureSnapshot = orderedSnapshot(recent),
            randomPriorities = mutableMapOf(),
            workKeys = { setOf(it.url) },
            nextRandomDouble = random::nextDouble,
        )
    }

    private fun orderedSnapshot(urls: List<String>): RecommendationExposureSnapshot {
        return RecommendationExposureSnapshot(
            urls.mapIndexed { index, url ->
                RecommendationExposure(
                    workFingerprints = setOf(url),
                    shownAtMillis = index.toLong(),
                    sequence = index.toLong(),
                )
            },
        )
    }

    private fun timedSnapshot(vararg exposures: Pair<String, Long>): RecommendationExposureSnapshot {
        return RecommendationExposureSnapshot(
            exposures.mapIndexed { index, (url, shownAtMillis) ->
                RecommendationExposure(
                    workFingerprints = setOf(url),
                    shownAtMillis = shownAtMillis,
                    sequence = index.toLong(),
                )
            },
        )
    }

    private fun ranked(
        url: String,
        score: Double,
        genres: Set<String> = setOf("romance"),
    ): RankedSimilarCandidate {
        val manga = SManga.create().apply {
            this.url = url
            title = url
        }
        return RankedSimilarCandidate(
            manga = manga,
            genres = genres,
            evidence = CandidateEvidence(strongRouteGenres = setOf("romance")),
            contentScore = score,
            score = score,
        )
    }
}
