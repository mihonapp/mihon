package eu.kanade.tachiyomi.ui.browse.migration.search

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.core.os.bundleOf
import eu.kanade.domain.manga.model.Manga
import eu.kanade.presentation.browse.SourceSearchScreen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.base.controller.pushController
import eu.kanade.tachiyomi.ui.base.controller.setRoot
import eu.kanade.tachiyomi.ui.browse.BrowseController
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceController
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourcePresenter
import eu.kanade.tachiyomi.ui.manga.MangaController
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

    @Composable
    override fun ComposeContent() {
        SourceSearchScreen(
            presenter = presenter,
            navigateUp = { router.popCurrentController() },
            onFabClick = { filterSheet?.show() },
            onMangaClick = {
                presenter.dialog = BrowseSourcePresenter.Dialog.Migrate(it)
            },
            onWebViewClick = f@{
                val source = presenter.source as? HttpSource ?: return@f
                activity?.let { context ->
                    val intent = WebViewActivity.newIntent(context, source.baseUrl, source.id, source.name)
                    context.startActivity(intent)
                }
            },
        )

        when (val dialog = presenter.dialog) {
            is BrowseSourcePresenter.Dialog.Migrate -> {
                MigrateDialog(
                    oldManga = oldManga!!,
                    newManga = dialog.newManga,
                    // TODO: Move screen model down into Dialog when this screen is using Voyager
                    screenModel = remember { MigrateDialogScreenModel() },
                    onDismissRequest = { presenter.dialog = null },
                    onClickTitle = { router.pushController(MangaController(dialog.newManga.id)) },
                    onPopScreen = {
                        // TODO: Push to manga screen and remove this and the previous screen when it moves to Voyager
                        router.setRoot(BrowseController(toExtensions = false), R.id.nav_browse)
                        router.pushController(MangaController(dialog.newManga.id))
                    },
                )
            }
            else -> {}
        }

        LaunchedEffect(presenter.filters) {
            initFilterSheet()
        }
    }
}

private const val MANGA_KEY = "oldManga"
