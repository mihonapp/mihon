package eu.kanade.tachiyomi.ui.browse.migration.advanced.design

import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.kanade.domain.source.service.SourcePreferences
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.injectLazy

class MigrationSourceAdapter(
    listener: OnItemClickListener,
) : FlexibleAdapter<MigrationSourceItem>(
    null,
    listener,
    true,
) {
    val sourceManager: SourceManager by injectLazy()

    val sourcePreferences: SourcePreferences by injectLazy()
}
