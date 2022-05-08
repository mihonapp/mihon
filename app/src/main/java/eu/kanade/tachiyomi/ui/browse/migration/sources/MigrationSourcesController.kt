package eu.kanade.tachiyomi.ui.browse.migration.sources

import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import eu.kanade.presentation.browse.MigrateSourceScreen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.base.controller.ComposeController
import eu.kanade.tachiyomi.ui.base.controller.pushController
import eu.kanade.tachiyomi.ui.browse.migration.manga.MigrationMangaController
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.system.openInBrowser

class MigrationSourcesController : ComposeController<MigrationSourcesPresenter>() {

    init {
        setHasOptionsMenu(true)
    }

    override fun createPresenter() = MigrationSourcesPresenter()

    @Composable
    override fun ComposeContent(nestedScrollInterop: NestedScrollConnection) {
        MigrateSourceScreen(
            nestedScrollInterop = nestedScrollInterop,
            presenter = presenter,
            onClickItem = { source ->
                parentController!!.router.pushController(
                    MigrationMangaController(
                        source.id,
                        source.name,
                    ),
                )
            },
            onLongClickItem = { source ->
                val sourceId = source.id.toString()
                activity?.copyToClipboard(sourceId, sourceId)
            },
        )
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) =
        inflater.inflate(R.menu.browse_migrate, menu)

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (val itemId = item.itemId) {
            R.id.action_source_migration_help -> {
                activity?.openInBrowser(HELP_URL)
                true
            }
            R.id.asc_alphabetical,
            R.id.desc_alphabetical,
            -> {
                presenter.setAlphabeticalSorting(itemId == R.id.asc_alphabetical)
                true
            }
            R.id.asc_count,
            R.id.desc_count,
            -> {
                presenter.setTotalSorting(itemId == R.id.asc_count)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}

private const val HELP_URL = "https://tachiyomi.org/help/guides/source-migration/"
