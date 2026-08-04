package eu.kanade.tachiyomi.data.recommendation

import eu.kanade.tachiyomi.source.model.SManga
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RecommendationRankingTest {

    @Test
    fun `tag profile keeps four highest IDF tags with stable ties`() {
        val tags = linkedSetOf("common", "rare", "second", "third", "fourth", "fifth")
        val profile = RecommendationRanking.buildTagProfile(
            targetTags = tags,
            documentFrequency = mapOf(
                "common" to 90,
                "rare" to 1,
                "second" to 5,
                "third" to 5,
                "fourth" to 10,
                "fifth" to 20,
            ),
            documentCount = 100,
        )

        assertEquals(listOf("rare", "second", "third", "fourth"), profile.coreTags.toList())
        assertEquals(setOf("common", "fifth"), profile.secondaryTags)
    }

    @Test
    fun `profile adapts at zero one four and many tags`() {
        listOf(0, 1, 4, 20).forEach { count ->
            val tags = (1..count).map { "tag_$it" }
            val profile = RecommendationRanking.buildTagProfile(tags, emptyMap(), 0)

            assertEquals(count, profile.allTags.size)
            assertEquals(minOf(count, TagProfile.MAX_CORE_TAGS), profile.coreTags.size)
            assertEquals((count - TagProfile.MAX_CORE_TAGS).coerceAtLeast(0), profile.secondaryTags.size)
        }
    }

    @Test
    fun `coverage does not punish candidate extra tags`() {
        val target = setOf("romance")
        val candidate = target + (1..20).map { "extra_$it" }

        assertEquals(
            1.0,
            RecommendationRanking.weightedCoverage(target, candidate, emptyMap(), 0),
            1.0e-12,
        )
        assertTrue(RecommendationRanking.weightedJaccard(target, candidate, emptyMap(), 0) < 0.05)
    }

    @Test
    fun `ranking requires tag evidence unless route is authoritative`() {
        val profile = RecommendationRanking.buildTagProfile(setOf("romance", "school"), emptyMap(), 0)
        val verified = candidate("verified", setOf("romance", "school", "comedy"))
        val sparse = candidate("sparse", setOf("romance"))
        val authoritative = candidate("authoritative", emptySet(), authoritative = true)

        val ranked = RecommendationRanking.rankSimilar(
            profile,
            listOf(verified, sparse, authoritative),
            emptyMap(),
            0,
        )

        assertEquals(setOf("verified", "authoritative"), ranked.map { it.card.manga.url }.toSet())
    }

    @Test
    fun `zero tag target only accepts authoritative evidence`() {
        val profile = RecommendationRanking.buildTagProfile(emptySet(), emptyMap(), 0)
        val ranked = RecommendationRanking.rankSimilar(
            profile,
            listOf(
                candidate("local", emptySet()),
                candidate("external", emptySet(), authoritative = true),
            ),
            emptyMap(),
            0,
        )

        assertEquals(listOf("external"), ranked.map { it.card.manga.url })
    }

    @Test
    fun `structured filter accepts missing metadata but rejects explicit tag conflicts`() {
        val profile = RecommendationRanking.buildTagProfile(setOf("romance"), emptyMap(), 0)
        val evidence = RecommendationEvidence(
            ranks = mapOf(RecommendationRoute.SOURCE_FILTER to 0),
            authoritative = true,
        )

        val ranked = RecommendationRanking.rankSimilar(
            profile = profile,
            candidates = listOf(
                RecommendationCandidate(card("missing", emptySet()), evidence),
                RecommendationCandidate(card("matching", setOf("romance", "school")), evidence),
                RecommendationCandidate(card("conflicting", setOf("horror")), evidence),
            ),
            documentFrequency = emptyMap(),
            documentCount = 0,
        )

        assertEquals(setOf("missing", "matching"), ranked.map { it.card.manga.url }.toSet())
    }

    @Test
    fun `RRF is normalized and decreases with route rank`() {
        val first = RecommendationRanking.normalizedRrf(
            RecommendationEvidence(mapOf(RecommendationRoute.ANILIST to 0)),
        )
        val later = RecommendationRanking.normalizedRrf(
            RecommendationEvidence(mapOf(RecommendationRoute.ANILIST to 20)),
        )

        assertEquals(1.0, first, 1.0e-12)
        assertTrue(later in 0.0..<first)
    }

    @Test
    fun `creator works require exact normalized creator and remain source scoped`() {
        val target = card("target", setOf("romance"), author = "Creator")
        val exact = card("exact", setOf("school"), author = "\uFF23\uFF32\uFF25\uFF21\uFF34\uFF2F\uFF32")
        val partial = card("partial", setOf("school"), author = "Creator Extra")
        val otherSource = card("other", setOf("school"), author = "Creator", sourceId = 2L)

        val selected = RecommendationCreators.selectWorks(target, listOf(exact, partial, otherSource))

        assertEquals(listOf("exact"), selected.map { it.manga.url })
    }

    @Test
    fun `sampling is stable quality weighted and diversity aware`() {
        val candidates = listOf(
            ranked("a", setOf("romance", "school"), 0.8),
            ranked("b", setOf("romance", "school"), 0.8),
            ranked("c", setOf("fantasy"), 0.8),
        )
        val first = RecommendationSampler.sample(candidates, 2, seed = 42L)
        val second = RecommendationSampler.sample(candidates, 2, seed = 42L)

        assertEquals(first.map { it.manga.url }, second.map { it.manga.url })
        var homogeneous = 0
        var diversified = 0
        repeat(500) { seed ->
            val urls = RecommendationSampler.sample(candidates, 2, seed.toLong()).map { it.manga.url }.toSet()
            if (urls == setOf("a", "b")) homogeneous++ else diversified++
        }
        assertTrue(diversified > homogeneous)
    }

    @Test
    fun `sampler prefers unseen then refills the oldest exposure`() {
        var now = 0L
        val store = RecommendationExposureStore(nowMillis = { now })
        val a = ranked("a", setOf("romance"), 0.8)
        val b = ranked("b", setOf("romance"), 0.8)
        val c = ranked("c", setOf("romance"), 0.8)
        store.record(1L, "target", listOf(a.card))
        now = 1L
        store.record(1L, "target", listOf(b.card))

        val sampled = RecommendationSampler.sample(
            listOf(a, b, c),
            maxResults = 2,
            seed = 7L,
            exposureSnapshot = store.snapshot(1L, "target"),
        )

        assertEquals("c", sampled.first().manga.url)
        assertEquals("a", sampled.last().manga.url)
    }

    @Test
    fun `exposure store is bounded isolated and expires keys`() {
        var now = 0L
        val store = RecommendationExposureStore(nowMillis = { now }, capacity = 2, ttlMillis = 100L)
        val a = card("a", emptySet())
        val b = card("b", emptySet())
        val c = card("c", emptySet())
        store.record(1L, "target", listOf(a, b, c))

        val snapshot = store.snapshot(1L, "target")
        assertFalse(snapshot.wasShown(a.identity.exposureKeys))
        assertTrue(snapshot.wasShown(b.identity.exposureKeys))
        assertFalse(store.snapshot(2L, "target").wasShown(b.identity.exposureKeys))
        now = 100L
        assertFalse(store.snapshot(1L, "target").wasShown(c.identity.exposureKeys))
    }

    private fun candidate(
        url: String,
        tags: Set<String>,
        authoritative: Boolean = false,
    ): RecommendationCandidate {
        return RecommendationCandidate(
            card(url, tags),
            RecommendationEvidence(mapOf(RecommendationRoute.LOCAL to 0), authoritative),
        )
    }

    private fun ranked(url: String, tags: Set<String>, score: Double): RankedRecommendation {
        return RankedRecommendation(
            card = card(url, tags),
            tags = tags,
            evidence = RecommendationEvidence(mapOf(RecommendationRoute.LOCAL to 0)),
            contentScore = score,
            score = score,
        )
    }

    private fun card(
        url: String,
        tags: Set<String>,
        author: String? = null,
        sourceId: Long = 1L,
    ): RecommendationCard {
        val manga = SManga.create().apply {
            this.url = url
            title = url
            genre = tags.joinToString(", ")
            this.author = author
        }
        return RecommendationMetadata.card(sourceId, manga)
    }
}
