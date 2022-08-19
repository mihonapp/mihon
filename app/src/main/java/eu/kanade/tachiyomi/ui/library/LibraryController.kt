package eu.kanade.tachiyomi.ui.library

import android.os.Bundle
import android.view.Menu
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.bluelinelabs.conductor.ControllerChangeHandler
import com.bluelinelabs.conductor.ControllerChangeType
import eu.kanade.domain.category.model.Category
import eu.kanade.domain.manga.model.Manga
import eu.kanade.domain.manga.model.toDbManga
import eu.kanade.presentation.library.LibraryScreen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.toDomainManga
import eu.kanade.tachiyomi.data.library.LibraryUpdateService
import eu.kanade.tachiyomi.ui.base.controller.FullComposeController
import eu.kanade.tachiyomi.ui.base.controller.RootController
import eu.kanade.tachiyomi.ui.base.controller.pushController
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchController
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.lang.withUIContext
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.widget.materialdialogs.QuadStateTextView
import kotlinx.coroutines.cancel

class LibraryController(
    bundle: Bundle? = null,
) : FullComposeController<LibraryPresenter>(bundle),
    RootController,
    ChangeMangaCategoriesDialog.Listener,
    DeleteLibraryMangasDialog.Listener {

    /**
     * Sheet containing filter/sort/display items.
     */
    private var settingsSheet: LibrarySettingsSheet? = null

    override fun createPresenter(): LibraryPresenter = LibraryPresenter()

    @Composable
    override fun ComposeContent() {
        val context = LocalContext.current
        LibraryScreen(
            presenter = presenter,
            onMangaClicked = ::openManga,
            onGlobalSearchClicked = {
                router.pushController(GlobalSearchController(presenter.searchQuery))
            },
            onChangeCategoryClicked = ::showMangaCategoriesDialog,
            onMarkAsReadClicked = { markReadStatus(true) },
            onMarkAsUnreadClicked = { markReadStatus(false) },
            onDownloadClicked = ::downloadUnreadChapters,
            onDeleteClicked = ::showDeleteMangaDialog,
            onClickFilter = ::showSettingsSheet,
            onClickRefresh = {
                val started = LibraryUpdateService.start(context, it)
                context.toast(if (started) R.string.updating_library else R.string.update_already_running)
                started
            },
            onClickInvertSelection = { presenter.invertSelection(presenter.activeCategory) },
            onClickSelectAll = { presenter.selectAll(presenter.activeCategory) },
            onClickUnselectAll = ::clearSelection,
        )
        LaunchedEffect(presenter.selectionMode) {
            val activity = (activity as? MainActivity) ?: return@LaunchedEffect
            // Could perhaps be removed when navigation is in a Compose world
            if (router.backstackSize == 1) {
                activity.showBottomNav(presenter.selectionMode.not())
            }
        }
        LaunchedEffect(presenter.isLoading) {
            if (presenter.isLoading.not()) {
                (activity as? MainActivity)?.ready = true
            }
        }
    }

    override fun handleBack(): Boolean {
        return when {
            presenter.selection.isNotEmpty() -> {
                presenter.clearSelection()
                true
            }
            presenter.searchQuery != null -> {
                presenter.searchQuery = null
                true
            }
            else -> false
        }
    }

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)

        settingsSheet = LibrarySettingsSheet(router) { group ->
            when (group) {
                is LibrarySettingsSheet.Filter.FilterGroup -> onFilterChanged()
                is LibrarySettingsSheet.Sort.SortGroup -> onSortChanged()
                is LibrarySettingsSheet.Display.DisplayGroup -> {}
                is LibrarySettingsSheet.Display.BadgeGroup -> onBadgeSettingChanged()
                is LibrarySettingsSheet.Display.TabsGroup -> {} // onTabsSettingsChanged()
            }
        }
    }

    override fun onChangeStarted(handler: ControllerChangeHandler, type: ControllerChangeType) {
        super.onChangeStarted(handler, type)
        if (type.isEnter) {
            presenter.subscribeLibrary()
        }
    }

    override fun onDestroyView(view: View) {
        settingsSheet?.sheetScope?.cancel()
        settingsSheet = null
        super.onDestroyView(view)
    }

    fun showSettingsSheet() {
        presenter.categories.getOrNull(presenter.activeCategory)?.let { category ->
            settingsSheet?.show(category)
        }
    }

    private fun onFilterChanged() {
        presenter.requestFilterUpdate()
        activity?.invalidateOptionsMenu()
    }

    private fun onBadgeSettingChanged() {
        presenter.requestBadgesUpdate()
    }

    private fun onSortChanged() {
        presenter.requestSortUpdate()
    }

    fun search(query: String) {
        presenter.searchQuery = query
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        val settingsSheet = settingsSheet ?: return
        presenter.hasActiveFilters = settingsSheet.filters.hasActiveFilters()
    }

    private fun openManga(mangaId: Long) {
        // Notify the presenter a manga is being opened.
        presenter.onOpenManga()

        router.pushController(MangaController(mangaId))
    }

    /**
     * Clear all of the manga currently selected, and
     * invalidate the action mode to revert the top toolbar
     */
    fun clearSelection() {
        presenter.clearSelection()
    }

    /**
     * Move the selected manga to a list of categories.
     */
    private fun showMangaCategoriesDialog() {
        viewScope.launchIO {
            // Create a copy of selected manga
            val mangas = presenter.selection.toList()

            // Hide the default category because it has a different behavior than the ones from db.
            val categories = presenter.categories.filter { it.id != 0L }

            // Get indexes of the common categories to preselect.
            val common = presenter.getCommonCategories(mangas.mapNotNull { it.toDomainManga() })
            // Get indexes of the mix categories to preselect.
            val mix = presenter.getMixCategories(mangas.mapNotNull { it.toDomainManga() })
            val preselected = categories.map {
                when (it) {
                    in common -> QuadStateTextView.State.CHECKED.ordinal
                    in mix -> QuadStateTextView.State.INDETERMINATE.ordinal
                    else -> QuadStateTextView.State.UNCHECKED.ordinal
                }
            }.toTypedArray()
            withUIContext {
                ChangeMangaCategoriesDialog(this@LibraryController, mangas.mapNotNull { it.toDomainManga() }, categories, preselected)
                    .showDialog(router)
            }
        }
    }

    private fun downloadUnreadChapters() {
        val mangas = presenter.selection.toList()
        presenter.downloadUnreadChapters(mangas.mapNotNull { it.toDomainManga() })
        presenter.clearSelection()
    }

    private fun markReadStatus(read: Boolean) {
        val mangas = presenter.selection.toList()
        presenter.markReadStatus(mangas.mapNotNull { it.toDomainManga() }, read)
        presenter.clearSelection()
    }

    private fun showDeleteMangaDialog() {
        val mangas = presenter.selection.toList()
        DeleteLibraryMangasDialog(this, mangas.mapNotNull { it.toDomainManga() }).showDialog(router)
    }

    override fun updateCategoriesForMangas(mangas: List<Manga>, addCategories: List<Category>, removeCategories: List<Category>) {
        presenter.setMangaCategories(mangas, addCategories, removeCategories)
        presenter.clearSelection()
    }

    override fun deleteMangas(mangas: List<Manga>, deleteFromLibrary: Boolean, deleteChapters: Boolean) {
        presenter.removeMangas(mangas.map { it.toDbManga() }, deleteFromLibrary, deleteChapters)
        presenter.clearSelection()
    }
}
