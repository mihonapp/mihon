package eu.kanade.tachiyomi.ui.browse

import android.Manifest
import android.os.Bundle
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.core.os.bundleOf
import eu.kanade.presentation.browse.BrowseScreen
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
        BrowseScreen(
            startIndex = 1.takeIf { toExtensions },
            tabs = listOf(
                sourcesTab(router, presenter.sourcesPresenter),
                extensionsTab(router, presenter.extensionsPresenter),
                migrateSourcesTab(router, presenter.migrationSourcesPresenter),
            ),
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
