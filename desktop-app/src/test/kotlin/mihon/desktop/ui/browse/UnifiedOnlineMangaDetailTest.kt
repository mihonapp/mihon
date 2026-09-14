package mihon.desktop.ui.browse

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import mihon.desktop.DesktopRuntime
import mihon.desktop.extension.ExtensionStoreService
import mihon.desktop.ui.library.LibraryPresenter
import mihon.desktop.ui.library.MangaDetailActions
import mihon.extension.source.WindowsCatalogueSource
import mihon.extension.source.model.FilterList
import mihon.extension.source.model.MangasPage
import mihon.extension.source.model.Page
import mihon.extension.source.model.SChapter
import mihon.extension.source.model.SManga
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class UnifiedOnlineMangaDetailTest {
    @Test
    fun `online source opens the complete shared detail action surface without favoriting`() = runComposeUiTest {
        val runtime = DesktopRuntime.forTesting()
        val presenterScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val presenter = LibraryPresenter(
            repository = runtime.library,
            scope = presenterScope,
            preferences = runtime.preferences,
            downloader = runtime.downloader,
        )
        var edits = 0
        var categories = 0
        var tracking = 0
        try {
            runtime.preferences.update {
                setProperty(ExtensionStoreService.PREF_KEY_REPOSITORIES, "http://127.0.0.1:9/repo")
            }
            runtime.sourceManager.registerBuiltinSource(UnifiedDetailSource())

            setContent {
                val detailState by presenter.detailState.collectAsState()
                Box(Modifier.requiredSize(1200.dp, 800.dp)) {
                    BrowseContentView(
                        runtime = runtime,
                        detailState = detailState,
                        detailActions = MangaDetailActions(
                            onEditInfo = { edits++ },
                            onEditCategories = { categories++ },
                            onOpenTracking = { tracking++ },
                        ),
                        onOpenMangaDetail = presenter::openMangaDetail,
                        onCloseMangaDetail = { presenter.selectManga(null) },
                        onRetryMangaDetail = presenter::retryDetail,
                    )
                }
            }

            waitUntil(timeoutMillis = 15_000) {
                onAllNodesWithTag("source-item-${UnifiedDetailSource.ID}").fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithTag("source-item-${UnifiedDetailSource.ID}").performClick()
            waitUntil(timeoutMillis = 15_000) {
                onAllNodesWithTag("manga-card-${UnifiedDetailSource.MANGA_URL}").fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithTag("manga-card-${UnifiedDetailSource.MANGA_URL}").performClick()

            waitUntil(timeoutMillis = 15_000) {
                onAllNodesWithTag("manga-detail-edit-info-button").fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithTag("manga-detail-edit-info-button").assertIsDisplayed().performClick()
            onNodeWithTag("manga-detail-edit-categories-button").assertIsDisplayed().performClick()
            onNodeWithTag("manga-detail-open-tracking-button").assertIsDisplayed().performClick()
            onNodeWithTag("chapter-search-toggle-button").assertIsDisplayed()
            onNodeWithTag("chapter-selection-toggle-button").assertExists()
            onNodeWithTag("manga-detail-library-button").assertIsDisplayed()
            onNodeWithTag("manga-detail-refresh-button").assertIsDisplayed()

            edits shouldBe 1
            categories shouldBe 1
            tracking shouldBe 1
            runtime.library.allMangaSnapshot().single().favorite shouldBe false
            runtime.library.allChaptersSnapshot().single().name shouldBe "Chapter 1"
        } finally {
            presenter.close()
            presenterScope.cancel()
            runBlocking { runtime.shutdown() }
        }
    }

    private class UnifiedDetailSource : WindowsCatalogueSource {
        override val id = ID
        override val name = "Unified detail source"
        override val lang = "en"
        override suspend fun getPopularManga(page: Int) = MangasPage(
            mangas = listOf(SManga(url = MANGA_URL, title = "Online title", initialized = false)),
            hasNextPage = false,
        )
        override suspend fun getLatestUpdates(page: Int) = getPopularManga(page)
        override suspend fun searchManga(page: Int, query: String, filters: FilterList) = getPopularManga(page)
        override suspend fun getMangaDetails(manga: SManga) = manga.copy(
            author = "Online author",
            description = "Online description",
            initialized = true,
        )
        override suspend fun getChapterList(manga: SManga) =
            listOf(SChapter(url = "/chapter/1", name = "Chapter 1", chapterNumber = 1f))
        override suspend fun getPageList(chapter: SChapter): List<Page> = emptyList()

        companion object {
            const val ID = 9_101L
            const val MANGA_URL = "/unified/detail"
        }
    }
}
