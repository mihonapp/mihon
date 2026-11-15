package tachiyomi.domain.library.model

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.domain.library.service.HiddenLibrarySession
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.library.service.LibraryVisibility
import tachiyomi.domain.manga.repository.MangaRepository

class LibraryVisibilityQueryTest {
    @Test
    fun skipsDatabaseWhenNothingIsHiddenAndUsesDedicatedProjectionOtherwise() = runTest {
        val preferences = LibraryPreferences(InMemoryPreferenceStore())
        val repository = mockk<MangaRepository>()
        val visibility = LibraryVisibility(preferences, repository, HiddenLibrarySession())
        visibility.hiddenMangaIds() shouldBe emptySet()
        preferences.hiddenCategories.set(setOf("0", "3", "invalid"))
        coEvery { repository.getHiddenLibraryMangaIds(listOf(0L, 3L)) } returns setOf(1L, 5L)
        visibility.hiddenMangaIds() shouldBe setOf(1L, 5L)
        coVerify(exactly = 1) { repository.getHiddenLibraryMangaIds(listOf(0L, 3L)) }
        coVerify(exactly = 0) { repository.getLibraryManga() }
    }

    @Test
    fun hiddenTitlesStayPrivateEvenWhenLegacyVisibilityOptOutWasRestored() = runTest {
        val store = InMemoryPreferenceStore()
        val preferences = LibraryPreferences(store)
        preferences.hiddenCategories.set(setOf("4"))
        store.getBoolean("komikita_hide_categories_everywhere", true).set(false)
        val visibility = LibraryVisibility(preferences, mockk(), HiddenLibrarySession())
        visibility.hiddenCategoryIds() shouldBe listOf(4L)
    }

    @Test
    fun sourceVisibilityFollowsTheSettingCategoryMembershipAndRevealSession() = runTest {
        val enabled = MutableStateFlow(false)
        val categories = MutableStateFlow(setOf("0", "4", "invalid"))
        val sources = MutableStateFlow(setOf(12L, 24L))
        val preferences = mockk<LibraryPreferences>()
        every { preferences.hideSourcesInHiddenCategories.changes() } returns enabled
        every { preferences.hiddenCategories.changes() } returns categories
        val repository = mockk<MangaRepository>()
        every { repository.getHiddenLibrarySourceIdsAsFlow(listOf(0L, 4L)) } returns sources
        val session = HiddenLibrarySession()
        val visibility = LibraryVisibility(preferences, repository, session)
        val emissions = mutableListOf<Set<Long>>()
        backgroundScope.launch { visibility.hiddenSourceIdsFlow().collect { emissions.add(it) } }

        runCurrent()
        emissions shouldBe listOf(emptySet())
        verify(exactly = 0) { repository.getHiddenLibrarySourceIdsAsFlow(any()) }

        enabled.value = true
        runCurrent()
        emissions.last() shouldBe setOf(12L, 24L)

        session.unlock()
        runCurrent()
        emissions.last() shouldBe emptySet()

        sources.value = setOf(24L)
        session.lock()
        runCurrent()
        emissions.last() shouldBe setOf(24L)

        categories.value = emptySet()
        runCurrent()
        emissions.last() shouldBe emptySet()
        verify(exactly = 1) { repository.getHiddenLibrarySourceIdsAsFlow(any()) }
    }
}
