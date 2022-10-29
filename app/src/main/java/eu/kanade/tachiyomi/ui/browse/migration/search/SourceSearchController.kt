package eu.kanade.tachiyomi.ui.browse.migration.search

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.core.os.bundleOf
import eu.kanade.domain.manga.model.Manga
import eu.kanade.presentation.browse.SourceSearchScreen
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceController
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import eu.kanade.tachiyomi.util.system.getSerializableCompat

class SourceSearchController(
    bundle: Bundle,
) : BrowseSourceController(bundle) {

    constructor(manga: Manga? = null, source: CatalogueSource, searchQuery: String? = null) : this(
        bundleOf(
            SOURCE_ID_KEY to source.id,
            MANGA_KEY to manga,
            SEARCH_QUERY_KEY to searchQuery,
        ),
    )

    private var oldManga: Manga? = args.getSerializableCompat(MANGA_KEY)
    private var newManga: Manga? = null

    @Composable
    override fun ComposeContent() {
        SourceSearchScreen(
            presenter = presenter,
            navigateUp = { router.popCurrentController() },
            onFabClick = { filterSheet?.show() },
            onMangaClick = {
                newManga = it
                val searchController = router.backstack.findLast { it.controller.javaClass == SearchController::class.java }?.controller as SearchController?
                val dialog = SearchController.MigrationDialog(oldManga, newManga, this)
                dialog.targetController = searchController
                dialog.showDialog(router)
            },
            onWebViewClick = f@{
                val source = presenter.source as? HttpSource ?: return@f
                activity?.let { context ->
                    val intent = WebViewActivity.newIntent(context, source.baseUrl, source.id, source.name)
                    context.startActivity(intent)
                }
            },
        )

        LaunchedEffect(presenter.filters) {
            initFilterSheet()
        }
    }
}

private const val MANGA_KEY = "oldManga"
