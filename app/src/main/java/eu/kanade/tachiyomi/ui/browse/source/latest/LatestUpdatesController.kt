package eu.kanade.tachiyomi.ui.browse.source.latest

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.os.bundleOf
import eu.kanade.domain.source.model.Source
import eu.kanade.presentation.browse.BrowseLatestScreen
import eu.kanade.presentation.browse.components.RemoveMangaDialog
import eu.kanade.presentation.components.ChangeCategoryDialog
import eu.kanade.presentation.components.DuplicateMangaDialog
import eu.kanade.tachiyomi.ui.base.controller.pushController
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceController
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourcePresenter
import eu.kanade.tachiyomi.ui.category.CategoryController
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.util.lang.launchIO

/**
 * Controller that shows the latest manga from the catalogue. Inherit [BrowseSourceController].
 */
class LatestUpdatesController(bundle: Bundle) : BrowseSourceController(bundle) {

    constructor(source: Source) : this(
        bundleOf(SOURCE_ID_KEY to source.id),
    )

    override fun createPresenter(): BrowseSourcePresenter {
        return LatestUpdatesPresenter(args.getLong(SOURCE_ID_KEY))
    }

    @Composable
    override fun ComposeContent() {
        val scope = rememberCoroutineScope()

        BrowseLatestScreen(
            presenter = presenter,
            navigateUp = { router.popCurrentController() },
            onMangaClick = { router.pushController(MangaController(it.id, true)) },
            onMangaLongClick = { manga ->
                scope.launchIO {
                    val duplicateManga = presenter.getDuplicateLibraryManga(manga)
                    when {
                        manga.favorite -> presenter.dialog = BrowseSourcePresenter.Dialog.RemoveManga(manga)
                        duplicateManga != null -> presenter.dialog = BrowseSourcePresenter.Dialog.AddDuplicateManga(manga, duplicateManga)
                        else -> presenter.addFavorite(manga)
                    }
                }
            },
        )

        val onDismissRequest = { presenter.dialog = null }
        when (val dialog = presenter.dialog) {
            is BrowseSourcePresenter.Dialog.AddDuplicateManga -> {
                DuplicateMangaDialog(
                    onDismissRequest = onDismissRequest,
                    onOpenManga = {
                        router.pushController(MangaController(dialog.duplicate.id, true))
                    },
                    onConfirm = {
                        presenter.addFavorite(dialog.manga)
                    },
                    duplicateFrom = presenter.getSourceOrStub(dialog.manga),
                )
            }
            is BrowseSourcePresenter.Dialog.RemoveManga -> {
                RemoveMangaDialog(
                    onDismissRequest = onDismissRequest,
                    onConfirm = {
                        presenter.changeMangaFavorite(dialog.manga)
                    },
                )
            }
            is BrowseSourcePresenter.Dialog.ChangeMangaCategory -> {
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
    }

    override fun initFilterSheet() {
        // No-op: we don't allow filtering in latest
    }
}
