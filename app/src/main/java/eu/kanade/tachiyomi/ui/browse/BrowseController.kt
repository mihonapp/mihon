package eu.kanade.tachiyomi.ui.browse

import android.Manifest
import android.os.Bundle
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.os.bundleOf
import eu.kanade.presentation.components.TabbedScreen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.base.controller.FullComposeController
import eu.kanade.tachiyomi.ui.base.controller.RootController
import eu.kanade.tachiyomi.ui.base.controller.requestPermissionsSafe
import eu.kanade.tachiyomi.ui.browse.extension.extensionsTab
import eu.kanade.tachiyomi.ui.browse.migration.sources.migrateSourcesTab
import eu.kanade.tachiyomi.ui.browse.source.sourcesTab
import eu.kanade.tachiyomi.ui.main.MainActivity

class BrowseController : FullComposeController<BrowsePresenter>, RootController {

    @Suppress("unused")
    constructor(bundle: Bundle? = null) : this(bundle?.getBoolean(TO_EXTENSIONS_EXTRA) ?: false)

    constructor(toExtensions: Boolean = false) : super(
        bundleOf(TO_EXTENSIONS_EXTRA to toExtensions),
    )

    private val toExtensions = args.getBoolean(TO_EXTENSIONS_EXTRA, false)

    override fun createPresenter() = BrowsePresenter()

    @Composable
    override fun ComposeContent() {
        val query by presenter.extensionsPresenter.query.collectAsState()

        TabbedScreen(
            titleRes = R.string.browse,
            tabs = listOf(
                sourcesTab(router, presenter.sourcesPresenter),
                extensionsTab(router, presenter.extensionsPresenter),
                migrateSourcesTab(router, presenter.migrationSourcesPresenter),
            ),
            startIndex = 1.takeIf { toExtensions },
            searchQuery = query,
            onChangeSearchQuery = { presenter.extensionsPresenter.search(it) },
            incognitoMode = presenter.isIncognitoMode,
            downloadedOnlyMode = presenter.isDownloadOnly,
        )

        LaunchedEffect(Unit) {
            (activity as? MainActivity)?.ready = true
        }
    }

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)
        requestPermissionsSafe(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 301)
    }
}

private const val TO_EXTENSIONS_EXTRA = "to_extensions"
