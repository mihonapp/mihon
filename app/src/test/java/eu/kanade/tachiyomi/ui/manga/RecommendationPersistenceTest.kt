package eu.kanade.tachiyomi.ui.manga

import eu.kanade.tachiyomi.source.model.SManga
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga

class RecommendationPersistenceTest {

    @Test
    fun `stored local id is retained while fresh source card metadata wins`() {
        val stored = Manga.create().copy(
            id = 77L,
            source = 42L,
            url = "/work/1",
            title = "Old title",
            author = "Stored author",
            description = "Stored description",
            thumbnailUrl = "old-cover",
            initialized = true,
        )
        val fresh = Manga.create().copy(
            source = 42L,
            url = "/work/1",
            title = "Fresh title",
            author = null,
            description = "Fresh description",
            thumbnailUrl = "fresh-cover",
            status = SManga.COMPLETED.toLong(),
        )

        val merged = stored.withFreshRecommendationMetadata(fresh)

        assertEquals(77L, merged.id)
        assertEquals("Fresh title", merged.title)
        assertEquals("fresh-cover", merged.thumbnailUrl)
        assertEquals("Fresh description", merged.description)
        assertEquals("Stored author", merged.author)
        assertEquals(SManga.COMPLETED.toLong(), merged.status)
        assertTrue(merged.initialized)
    }

    @Test
    fun `bare existing row persists useful recommendation metadata without claiming initialization`() {
        val recommendation = Manga.create().copy(
            id = 77L,
            source = 42L,
            url = "/work/1",
            title = "Fresh title",
            author = "Fresh author",
            genre = listOf("romance", "school life"),
            status = SManga.ONGOING.toLong(),
            initialized = false,
        )

        val update = recommendation.toRecommendationMetadataUpdate(localId = 77L)!!

        assertEquals(77L, update.id)
        assertEquals("Fresh author", update.author)
        assertEquals(listOf("romance", "school life"), update.genre)
        assertEquals(SManga.ONGOING.toLong(), update.status)
        assertNull(update.initialized)
        assertFalse(recommendation.initialized)
    }

    @Test
    fun `empty bare card does not create a redundant metadata update`() {
        val recommendation = Manga.create().copy(id = 77L, title = "Only identity")

        assertNull(recommendation.toRecommendationMetadataUpdate(localId = 77L))
    }
}
