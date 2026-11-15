package eu.kanade.tachiyomi.ui.browse.source.globalsearch

import androidx.lifecycle.ViewModelStore
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import java.util.concurrent.atomic.AtomicInteger

class SearchConcurrencyTest {
    @Test
    fun globalSearchIncludesUnpinnedSourcesWithTheSameNameByDefault() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModels = ViewModelStore()
        try {
            val preferences = SourcePreferences(InMemoryPreferenceStore())
            preferences.enabledLanguages.set(setOf("en"))
            preferences.pinnedSources.set(setOf("1"))
            val sources = (1L..3L).map { id ->
                mockk<Source> {
                    every { this@mockk.id } returns id
                    every { name } returns "Source"
                    every { lang } returns "en"
                    every { getFilterList() } returns FilterList()
                    coEvery { getSearchManga(any(), any(), any()) } returns MangasPage(emptyList(), false)
                }
            }
            val sourceManager = mockk<SourceManager>()
            coEvery { sourceManager.getAll() } returns sources
            val extensionManager = mockk<ExtensionManager>()
            coEvery { extensionManager.getLoadedExtensions() } returns emptyList()
            val converter = mockk<NetworkToLocalManga>()
            coEvery { converter(any<List<Manga>>()) } returns emptyList()
            val model = GlobalSearchViewModel(
                initialQuery = "test",
                initialExtensionFilter = null,
                sourcePreferences = preferences,
                sourceManager = sourceManager,
                extensionManager = extensionManager,
                networkToLocalManga = converter,
                getManga = mockk(),
                preferences = preferences,
            )
            viewModels.put("search", model)

            val results = model.state.first { it.total == sources.size && it.progress == it.total }
            results.items.keys.toList() shouldBe sources

            model.setSourceFilter(SourceFilter.PinnedOnly)
            model.state.first { it.total == 1 && it.progress == 1 }.items.keys.single() shouldBe sources.first()
        } finally {
            viewModels.clear()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun atMostFiveSuspendingSourceOperationsRemainInFlight() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModels = ViewModelStore()
        try {
            val preferences = SourcePreferences(InMemoryPreferenceStore())
            val active = AtomicInteger()
            val maximum = AtomicInteger()
            val sources = (1L..20L).map { id ->
                mockk<Source> {
                    every { this@mockk.id } returns id
                    every { name } returns "Source $id"
                    every { lang } returns "en"
                    every { getFilterList() } returns FilterList()
                    coEvery { getSearchManga(any(), any(), any()) } coAnswers {
                        maximum.accumulateAndGet(active.incrementAndGet(), ::maxOf)
                        try {
                            delay(50)
                            MangasPage(emptyList(), false)
                        } finally {
                            active.decrementAndGet()
                        }
                    }
                }
            }
            val converter = mockk<NetworkToLocalManga>()
            coEvery { converter(any<List<Manga>>()) } returns emptyList()
            val model = object : SearchViewModel(
                SearchViewModel.State(searchQuery = "test"),
                preferences,
                mockk(),
                mockk(),
                converter,
                mockk(),
                preferences,
            ) {
                override suspend fun getEnabledSources(): List<Source> = sources
            }
            viewModels.put("search", model)
            model.search()
            model.state.first {
                it.items.size == 20 &&
                    it.items.values.none { value -> value is SearchItemResult.Loading }
            }
            maximum.get() shouldBe 5
            active.get() shouldBe 0
        } finally {
            viewModels.clear()
            Dispatchers.resetMain()
        }
    }
}
