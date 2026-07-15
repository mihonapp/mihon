package eu.kanade.tachiyomi.ui.manga

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga

class RecommendationRowStateTest {

    @Test
    fun `fresh partial results contain no cards from the previous manga`() {
        val state = listOf(manga("new-1"), manga("old-1"))
            .toRecommendationState() as RecommendationRowState.Success

        assertEquals(listOf("new-1", "old-1"), state.manga.map(Manga::url))
    }

    @Test
    fun `empty current-source response hides the row`() {
        val state = emptyList<Manga>().toRecommendationState()

        assertSame(RecommendationRowState.Hidden, state)
    }

    @Test
    fun `transient empty refresh retains this manga last successful row`() {
        val previous = RecommendationRowState.Success(listOf(manga("kept")))

        val state = resolveRecommendationRow(
            previous = previous,
            fresh = emptyList(),
            authoritative = false,
        )

        assertSame(previous, state)
    }

    private fun manga(url: String): Manga {
        return Manga.create().copy(
            source = 42,
            url = url,
            title = url,
            initialized = true,
        )
    }
}
