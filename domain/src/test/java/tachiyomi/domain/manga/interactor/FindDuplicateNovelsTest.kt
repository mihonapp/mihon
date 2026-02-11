package tachiyomi.domain.manga.interactor

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaWithChapterCount
import tachiyomi.domain.manga.repository.DuplicateGroup
import tachiyomi.domain.manga.repository.DuplicatePair
import tachiyomi.domain.manga.repository.MangaRepository

class FindDuplicateNovelsTest {

    private val mangaRepository = mockk<MangaRepository>()
    private val findDuplicateNovels = FindDuplicateNovels(mangaRepository)

    @Test
    fun `findDuplicatesGrouped EXACT mode returns grouped results`() = runBlocking {
        // Arrange
        val manga1 = mockk<Manga> {
            coEvery { id } returns 1L
            coEvery { title } returns "Novel A"
            coEvery { source } returns 1L
        }
        val manga2 = mockk<Manga> {
            coEvery { id } returns 2L
            coEvery { title } returns "Novel A"
            coEvery { source } returns 2L
        }
        val manga3 = mockk<Manga> {
            coEvery { id } returns 3L
            coEvery { title } returns "Unique Novel"
            coEvery { source } returns 1L
        }

        // The SQL query returns lower(trim(title)) as normalized_title
        val group = DuplicateGroup("novel a", listOf(1L, 2L), 2)
        val mangaWithCount1 = MangaWithChapterCount(manga1, 10, 5)
        val mangaWithCount2 = MangaWithChapterCount(manga2, 20, 10)

        coEvery { mangaRepository.findDuplicatesExact() } returns listOf(group)
        coEvery { mangaRepository.getMangaWithCounts(listOf(1L, 2L)) } returns listOf(mangaWithCount1, mangaWithCount2)

        // Act
        val result = findDuplicateNovels.findDuplicatesGrouped(DuplicateMatchMode.EXACT)

        // Assert
        assertEquals(1, result.size)
        assertTrue(result.containsKey("novel a"))
        assertEquals(2, result["novel a"]?.size)
    }

    @Test
    fun `findDuplicatesGrouped CONTAINS mode handles pairs correctly`() = runBlocking {
        // Arrange
        val manga1 = mockk<Manga> { coEvery { id } returns 1L }
        val manga2 = mockk<Manga> { coEvery { id } returns 2L }
        
        val pair = DuplicatePair(1L, "Short Title", 2L, "Longer Short Title")
        val mangaWithCount1 = MangaWithChapterCount(manga1, 10, 0)
        val mangaWithCount2 = MangaWithChapterCount(manga2, 10, 0)

        coEvery { mangaRepository.findDuplicatesContains() } returns listOf(pair)
        coEvery { mangaRepository.getMangaWithCounts(listOf(1L, 2L)) } returns listOf(mangaWithCount1, mangaWithCount2)

        // Act
        val result = findDuplicateNovels.findDuplicatesGrouped(DuplicateMatchMode.CONTAINS)

        // Assert
        // Logic: key = shortest title (lower trimmed)
        // "Short Title" -> "short title"
        
        assertTrue(result.containsKey("short title"))
        assertEquals(2, result["short title"]?.size)
    }
}
