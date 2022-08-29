package eu.kanade.tachiyomi.ui.browse

import android.os.Bundle
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.ui.browse.extension.ExtensionsPresenter
import eu.kanade.tachiyomi.ui.browse.migration.sources.MigrationSourcesPresenter
import eu.kanade.tachiyomi.ui.browse.source.SourcesPresenter
import uy.kohesive.injekt.api.get

class BrowsePresenter : BasePresenter<BrowseController>() {

    val sourcesPresenter = SourcesPresenter(presenterScope)
    val extensionsPresenter = ExtensionsPresenter(presenterScope)
    val migrationSourcesPresenter = MigrationSourcesPresenter(presenterScope)

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        sourcesPresenter.onCreate()
        extensionsPresenter.onCreate()
        migrationSourcesPresenter.onCreate()
    }
}
