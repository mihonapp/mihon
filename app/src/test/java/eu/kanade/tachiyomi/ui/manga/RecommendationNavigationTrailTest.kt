package eu.kanade.tachiyomi.ui.manga

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RecommendationNavigationTrailTest {

    @Test
    fun `a recommendation child inherits only same-source ancestors and its parent`() {
        val child = nextRecommendationAncestorUrls(
            trailSourceId = 42,
            ancestorUrls = listOf("root"),
            currentSourceId = 42,
            currentUrl = "parent",
        )

        assertEquals(listOf("root", "parent"), child)
        assertEquals(
            setOf("root", "parent"),
            recommendationAncestorUrlsForSource(42, child, currentSourceId = 42),
        )
    }

    @Test
    fun `a different source starts a fresh recommendation trail`() {
        val child = nextRecommendationAncestorUrls(
            trailSourceId = 41,
            ancestorUrls = listOf("other-source-root"),
            currentSourceId = 42,
            currentUrl = "current-source-root",
        )

        assertEquals(listOf("current-source-root"), child)
        assertTrue(
            recommendationAncestorUrlsForSource(41, listOf("same-url"), currentSourceId = 42).isEmpty(),
        )
    }

    @Test
    fun `building a deeper trail does not mutate parent back-stack snapshots`() {
        val root = emptyList<String>()
        val second = nextRecommendationAncestorUrls(null, root, currentSourceId = 42, currentUrl = "a")
        val third = nextRecommendationAncestorUrls(42, second, currentSourceId = 42, currentUrl = "b")

        assertTrue(root.isEmpty())
        assertEquals(listOf("a"), second)
        assertEquals(listOf("a", "b"), third)
    }

    @Test
    fun `trail keeps the eight most recent unique nonblank urls`() {
        val child = nextRecommendationAncestorUrls(
            trailSourceId = 42,
            ancestorUrls = (1..9).map { "manga-$it" } + listOf("", "manga-5"),
            currentSourceId = 42,
            currentUrl = "manga-10",
        )

        assertEquals(
            listOf("manga-3", "manga-4", "manga-6", "manga-7", "manga-8", "manga-9", "manga-5", "manga-10"),
            child,
        )
    }

    @Test
    fun `work identity aliases follow the same source recommendation trail`() {
        val child = nextRecommendationAncestorWorkKeys(
            trailSourceId = 42,
            ancestorWorkKeys = listOf("42:url:root"),
            currentSourceId = 42,
            currentWorkKeys = setOf("42:url:parent", "42:title:parent:alice"),
        )

        assertEquals(
            setOf("42:url:root", "42:url:parent", "42:title:parent:alice"),
            recommendationAncestorWorkKeysForSource(42, child, currentSourceId = 42),
        )
    }

    @Test
    fun `work identity trail is discarded when source changes`() {
        assertTrue(
            recommendationAncestorWorkKeysForSource(
                trailSourceId = 41,
                ancestorWorkKeys = listOf("41:title:old:alice"),
                currentSourceId = 42,
            ).isEmpty(),
        )
    }
}
