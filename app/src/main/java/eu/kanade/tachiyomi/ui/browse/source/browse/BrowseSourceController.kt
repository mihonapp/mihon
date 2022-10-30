package eu.kanade.tachiyomi.ui.browse.source.browse

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.os.bundleOf
import eu.kanade.domain.source.model.Source
import eu.kanade.presentation.browse.BrowseSourceScreen
import eu.kanade.presentation.browse.components.RemoveMangaDialog
import eu.kanade.presentation.components.ChangeCategoryDialog
import eu.kanade.presentation.components.DuplicateMangaDialog
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.base.controller.FullComposeController
import eu.kanade.tachiyomi.ui.base.controller.pushController
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourcePresenter.Dialog
import eu.kanade.tachiyomi.ui.category.CategoryController
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import eu.kanade.tachiyomi.util.lang.launchIO

open class BrowseSourceController(bundle: Bundle) :
    FullComposeController<BrowseSourcePresenter>(bundle) {

    constructor(sourceId: Long, query: String? = null) : this(
        bundleOf(
            SOURCE_ID_KEY to sourceId,
            SEARCH_QUERY_KEY to query,
        ),
    )

    constructor(source: CatalogueSource, query: String? = null) : this(source.id, query)

    constructor(source: Source, query: String? = null) : this(source.id, query)

    /**
     * Sheet containing filter items.
     */
    protected var filterSheet: SourceFilterSheet? = null

    override fun createPresenter(): BrowseSourcePresenter {
        return BrowseSourcePresenter(args.getLong(SOURCE_ID_KEY), args.getString(SEARCH_QUERY_KEY))
    }

    @Composable
    override fun ComposeContent() {
        val scope = rememberCoroutineScope()
        val context = LocalContext.current

        BrowseSourceScreen(
            presenter = presenter,
            navigateUp = ::navigateUp,
            openFilterSheet = { filterSheet?.show() },
            onMangaClick = { router.pushController(MangaController(it.id, true)) },
            onMangaLongClick = { manga ->
                scope.launchIO {
                    val duplicateManga = presenter.getDuplicateLibraryManga(manga)
                    when {
                        manga.favorite -> presenter.dialog = Dialog.RemoveManga(manga)
                        duplicateManga != null -> presenter.dialog = Dialog.AddDuplicateManga(manga, duplicateManga)
                        else -> presenter.addFavorite(manga)
                    }
                }
            },
            onWebViewClick = f@{
                val source = presenter.source as? HttpSource ?: return@f
                val intent = WebViewActivity.newIntent(context, source.baseUrl, source.id, source.name)
                context.startActivity(intent)
            },
            incognitoMode = presenter.isIncognitoMode,
            downloadedOnlyMode = presenter.isDownloadOnly,
        )

        val onDismissRequest = { presenter.dialog = null }
        when (val dialog = presenter.dialog) {
            is Dialog.AddDuplicateManga -> {
                DuplicateMangaDialog(
                    onDismissRequest = onDismissRequest,
                    onConfirm = { presenter.addFavorite(dialog.manga) },
                    onOpenManga = { router.pushController(MangaController(dialog.duplicate.id)) },
                    duplicateFrom = presenter.getSourceOrStub(dialog.duplicate),
                )
            }
            is Dialog.RemoveManga -> {
                RemoveMangaDialog(
                    onDismissRequest = onDismissRequest,
                    onConfirm = {
                        presenter.changeMangaFavorite(dialog.manga)
                    },
                    mangaToRemove = dialog.manga,
                )
            }
            is Dialog.ChangeMangaCategory -> {
                ChangeCategoryDialog(
                    initialSelection = dialog.initialSelection,
                    onDismissRequest = onDismissRequest,
                    onEditCategories = {
                        router.pushController(CategoryController())
                    },
                    onConfirm = { include, _ ->
                        presenter.changeMangaFavorite(dialog.manga)
                        presenter.moveMangaToCategories(dialog.manga, include)
                    },
                )
            }
            null -> {}
        }

        BackHandler(onBack = ::navigateUp)

        LaunchedEffect(presenter.filters) {
            initFilterSheet()
        }
    }

    private fun navigateUp() {
        when {
            !presenter.isUserQuery && presenter.searchQuery != null -> presenter.searchQuery = null
            else -> router.popCurrentController()
        }
    }

    open fun initFilterSheet() {
        if (presenter.filters.isEmpty()) {
            return
        }

        filterSheet = SourceFilterSheet(
            activity!!,
            onFilterClicked = {
                presenter.search(filters = presenter.filters)
            },
            onResetClicked = {
                presenter.reset()
                filterSheet?.setFilters(presenter.filterItems)
            },
        )

        filterSheet?.setFilters(presenter.filterItems)
    }

    /**
     * Restarts the request with a new query.
     *
     * @param newQuery the new query.
     */
    fun searchWithQuery(newQuery: String) {
        presenter.search(newQuery)
    }

    /**
     * Attempts to restart the request with a new genre-filtered query.
     * If the genre name can't be found the filters,
     * the standard searchWithQuery search method is used instead.
     *
     * @param genreName the name of the genre
     */
    fun searchWithGenre(genreName: String) {
        val defaultFilters = presenter.source!!.getFilterList()

        var genreExists = false

        filter@ for (sourceFilter in defaultFilters) {
            if (sourceFilter is Filter.Group<*>) {
                for (filter in sourceFilter.state) {
                    if (filter is Filter<*> && filter.name.equals(genreName, true)) {
                        when (filter) {
                            is Filter.TriState -> filter.state = 1
                            is Filter.CheckBox -> filter.state = true
                            else -> {}
                        }
                        genreExists = true
                        break@filter
                    }
                }
            } else if (sourceFilter is Filter.Select<*>) {
                val index = sourceFilter.values.filterIsInstance<String>()
                    .indexOfFirst { it.equals(genreName, true) }

                if (index != -1) {
                    sourceFilter.state = index
                    genreExists = true
                    break
                }
            }
        }

        if (genreExists) {
            filterSheet?.setFilters(defaultFilters.toItems())

            presenter.search(filters = defaultFilters)
        } else {
            searchWithQuery(genreName)
        }
    }

    protected companion object {
        const val SOURCE_ID_KEY = "sourceId"
        const val SEARCH_QUERY_KEY = "searchQuery"
    }
}
