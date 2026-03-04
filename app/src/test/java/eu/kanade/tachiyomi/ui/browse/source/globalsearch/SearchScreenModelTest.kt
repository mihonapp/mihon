package eu.kanade.tachiyomi.ui.browse.source.globalsearch

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.ExtensionManager
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.source.service.SourceManager
import kotlin.time.Duration.Companion.seconds

class SearchScreenModelTest {

    companion object {
        @OptIn(DelicateCoroutinesApi::class)
        val mainThreadSurrogate = newSingleThreadContext("UI thread")

        @BeforeAll
        @JvmStatic
        fun setUp() {
            Dispatchers.setMain(mainThreadSurrogate)
        }

        @AfterAll
        @JvmStatic
        fun tearDown() {
            Dispatchers.resetMain()
            mainThreadSurrogate.close()
        }
    }

    private fun mockPreference(value: Set<String>): Preference<Set<String>> = mockk {
        every { get() } returns value
        every { changes() } returns MutableStateFlow(value)
    }

    private fun createModel(
        pinnedSources: Set<String> = emptySet(),
        pinnedOnlyFlow: MutableStateFlow<Boolean> = MutableStateFlow(false),
    ): Pair<TestSearchScreenModel, MutableStateFlow<Boolean>> {
        val filterStateFlow = MutableStateFlow(false)

        val pinnedOnlyPref = mockk<Preference<Boolean>> {
            every { get() } returns pinnedOnlyFlow.value
            every { changes() } returns pinnedOnlyFlow
        }
        val filterStatePref = mockk<Preference<Boolean>> {
            every { get() } returns false
            every { changes() } returns filterStateFlow
        }

        val sourcePreferences = mockk<SourcePreferences> {
            every { pinnedSources() } returns mockPreference(pinnedSources)
            every { enabledLanguages() } returns mockPreference(setOf("en"))
            every { disabledSources() } returns mockPreference(emptySet())
            every { globalSearchPinnedOnly() } returns pinnedOnlyPref
            every { globalSearchFilterState() } returns filterStatePref
        }

        val model = TestSearchScreenModel(
            sourcePreferences = sourcePreferences,
            sourceManager = mockk(relaxed = true),
            extensionManager = mockk(relaxed = true),
            networkToLocalManga = mockk(relaxed = true),
            getManga = mockk(relaxed = true),
            preferences = sourcePreferences,
        )

        return model to pinnedOnlyFlow
    }

    @Test
    fun `pinnedOnly is false when preference is true but no pinned sources`() {
        runBlocking {
            val pinnedOnlyFlow = MutableStateFlow(false)
            val (model, _) = createModel(
                pinnedSources = emptySet(),
                pinnedOnlyFlow = pinnedOnlyFlow,
            )

            pinnedOnlyFlow.value = true

            eventually(2.seconds) {
                model.state.value.pinnedOnly shouldBe false
                model.state.value.hasPinnedSources shouldBe false
            }
        }
    }

    @Test
    fun `pinnedOnly is true when preference is true and pinned sources exist`() {
        runBlocking {
            val pinnedOnlyFlow = MutableStateFlow(false)
            val (model, _) = createModel(
                pinnedSources = setOf("1"),
                pinnedOnlyFlow = pinnedOnlyFlow,
            )

            pinnedOnlyFlow.value = true

            eventually(2.seconds) {
                model.state.value.pinnedOnly shouldBe true
                model.state.value.hasPinnedSources shouldBe true
            }
        }
    }

    @Test
    fun `pinnedOnly is false when preference is false regardless of pinned sources`() {
        runBlocking {
            val pinnedOnlyFlow = MutableStateFlow(false)
            val (model, _) = createModel(
                pinnedSources = setOf("1"),
                pinnedOnlyFlow = pinnedOnlyFlow,
            )

            // pinnedOnlyFlow stays false
            eventually(2.seconds) {
                model.state.value.pinnedOnly shouldBe false
                model.state.value.hasPinnedSources shouldBe true
            }
        }
    }
}

private class TestSearchScreenModel(
    sourcePreferences: SourcePreferences,
    sourceManager: SourceManager,
    extensionManager: ExtensionManager,
    networkToLocalManga: NetworkToLocalManga,
    getManga: GetManga,
    preferences: SourcePreferences,
) : SearchScreenModel(
    initialState = State(),
    sourcePreferences = sourcePreferences,
    sourceManager = sourceManager,
    extensionManager = extensionManager,
    networkToLocalManga = networkToLocalManga,
    getManga = getManga,
    preferences = preferences,
)
