package exh.ui.migration.manga.design

import eu.davidea.flexibleadapter.FlexibleAdapter

class MigrationSourceAdapter(val items: List<MigrationSourceItem>,
                             val controller: MigrationDesignController): FlexibleAdapter<MigrationSourceItem>(
        items,
        controller,
        true
)