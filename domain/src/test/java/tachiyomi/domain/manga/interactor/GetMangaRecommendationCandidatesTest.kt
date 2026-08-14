package tachiyomi.domain.manga.interactor

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository

class GetMangaRecommendationCandidatesTest {

    private val mangaRepository = mockk<MangaRepository>()
    private val getMangaRecommendationCandidates = GetMangaRecommendationCandidates(mangaRepository)

    @Test
    fun `returns source scoped candidates while forwarding the excluded url`() = runTest {
        val sourceId = 42L
        val excludedUrl = "/manga/current"
        val candidates = listOf(
            Manga.create().copy(id = 1L, source = sourceId, url = "/manga/candidate", initialized = true),
        )
        coEvery {
            mangaRepository.getRecommendationCandidates(sourceId, excludedUrl)
        } returns candidates

        getMangaRecommendationCandidates.await(sourceId, excludedUrl) shouldBe candidates

        coVerify(exactly = 1) {
            mangaRepository.getRecommendationCandidates(sourceId, excludedUrl)
        }
    }
}
