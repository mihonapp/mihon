package eu.kanade.tachiyomi.ui.migration

import android.app.Dialog
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.appcompat.widget.SearchView
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.listItemsMultiChoice
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.ui.base.controller.withFadeTransaction
import eu.kanade.tachiyomi.ui.migration.manga.process.MigrationListController
import eu.kanade.tachiyomi.ui.source.globalsearch.GlobalSearchController
import eu.kanade.tachiyomi.ui.source.globalsearch.GlobalSearchPresenter
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import reactivecircus.flowbinding.appcompat.QueryTextEvent
import reactivecircus.flowbinding.appcompat.queryTextEvents
import uy.kohesive.injekt.injectLazy

class SearchController(
    private var manga: Manga? = null
) : GlobalSearchController(manga?.title) {

    private var newManga: Manga? = null
    private var progress = 1
    var totalProgress = 0

    /**
     * Called when controller is initialized.
     */
    init {
        setHasOptionsMenu(true)
    }

    override fun getTitle(): String? {
        if (totalProgress > 1) {
            return "($progress/$totalProgress) ${super.getTitle()}"
        } else {
            return super.getTitle()
        }
    }

    override fun createPresenter(): GlobalSearchPresenter {
        return SearchPresenter(initialQuery, manga!!)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putSerializable(::manga.name, manga)
        outState.putSerializable(::newManga.name, newManga)
        super.onSaveInstanceState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        manga = savedInstanceState.getSerializable(::manga.name) as? Manga
        newManga = savedInstanceState.getSerializable(::newManga.name) as? Manga
    }

    /*override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        if (totalProgress > 1) {
            val menuItem = menu.add(Menu.NONE, 1, Menu.NONE, R.string.action_skip_manga)
            menuItem.icon = VectorDrawableCompat.create(resources!!, R.drawable
                .baseline_skip_next_white_24, null)
            menuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            1 -> {
                newManga = manga
                migrateManga()
            }
        }
        return true
    }*/

    fun migrateManga() {
        val target = targetController as? MigrationInterface ?: return
        val manga = manga ?: return
        val newManga = newManga ?: return

        val nextManga = target.migrateManga(manga, newManga, true)
        replaceWithNewSearchController(nextManga)
    }

    fun copyManga() {
        val target = targetController as? MigrationInterface ?: return
        val manga = manga ?: return
        val newManga = newManga ?: return

        val nextManga = target.migrateManga(manga, newManga, false)
        replaceWithNewSearchController(nextManga)
    }

    private fun replaceWithNewSearchController(manga: Manga?) {
        if (manga != null) {
            // router.popCurrentController()
            val searchController = SearchController(manga)
            searchController.targetController = targetController
            searchController.progress = progress + 1
            searchController.totalProgress = totalProgress
            router.replaceTopController(searchController.withFadeTransaction())
        } else router.popController(this)
    }

    override fun onMangaClick(manga: Manga) {
        if (targetController is MigrationListController) {
            val migrationListController = targetController as? MigrationListController
            val sourceManager: SourceManager by injectLazy()
            val source = sourceManager.get(manga.source) ?: return
            migrationListController?.useMangaForMigration(manga, source)
            router.popCurrentController()
            return
        }
        newManga = manga
        val dialog = MigrationDialog()
        dialog.targetController = this
        dialog.showDialog(router)
    }

    override fun onMangaLongClick(manga: Manga) {
        // Call parent's default click listener
        super.onMangaClick(manga)
    }

    class MigrationDialog : DialogController() {

        private val preferences: PreferencesHelper by injectLazy()

        override fun onCreateDialog(savedViewState: Bundle?): Dialog {
            val prefValue = preferences.migrateFlags().get()

            val preselected = MigrationFlags.getEnabledFlagsPositions(prefValue)

            return MaterialDialog(activity!!)
                .message(R.string.data_to_include_in_migration)
                .listItemsMultiChoice(
                    items = MigrationFlags.titles.map { resources?.getString(it) as CharSequence },
                    initialSelection = preselected.toIntArray()
                ) { _, positions, _ ->
                    // Save current settings for the next time
                    val newValue = MigrationFlags.getFlagsFromPositions(positions.toTypedArray())
                    preferences.migrateFlags().set(newValue)
                }
                .positiveButton(R.string.migrate) {
                    (targetController as? SearchController)?.migrateManga()
                }
                .negativeButton(R.string.copy) {
                    (targetController as? SearchController)?.copyManga()
                }
                .neutralButton(android.R.string.cancel)
        }
    }

    /**
     * Adds items to the options menu.
     *
     * @param menu menu containing options.
     * @param inflater used to load the menu xml.
     */
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        // Inflate menu.
        inflater.inflate(R.menu.source_browse, menu)

        // Initialize search menu
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView

        searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem?): Boolean {
                searchView.onActionViewExpanded() // Required to show the query in the view
                searchView.setQuery(presenter.query, false)
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
                return true
            }
        })

        searchView.queryTextEvents()
            .filter { it is QueryTextEvent.QuerySubmitted }
            .onEach {
                presenter.search(it.queryText.toString())
                searchItem.collapseActionView()
                setTitle() // Update toolbar title
            }
            .launchIn(scope)
    }
}
