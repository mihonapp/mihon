package mihon.domain.upcoming.interactor

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.library.service.LibraryVisibility
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository

class GetUpcomingMangaTest {
    @Test
    fun excludesHiddenCategoriesAlongsideUserFilters() = runTest {
        val visible = mockk<Manga>()
        val repository = mockk<MangaRepository>()
        val hidden = MutableStateFlow(listOf(0L, 4L))
        val visibility = mockk<LibraryVisibility>()
        every { visibility.changes() } returns hidden
        coEvery { repository.getUpcomingManga(any(), listOf(4L, 5L, 0L), listOf(4L, 7L)) } returns flowOf(emptyList())
        coEvery { repository.getUpcomingManga(any(), listOf(4L, 5L), listOf(4L, 7L)) } returns flowOf(listOf(visible))

        val upcoming = GetUpcomingManga(repository, visibility).subscribe(
            excludedCategories = listOf(4L, 5L),
            includedCategories = listOf(4L, 7L),
        )

        val results = upcoming.take(2).onEach { hidden.value = emptyList() }.toList()
        results shouldBe listOf(emptyList(), listOf(visible))
    }
}
