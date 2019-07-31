package exh.ui.migration.manga.design

import android.os.Bundle
import eu.davidea.flexibleadapter.FlexibleAdapter
import exh.debug.DebugFunctions.sourceManager

class MigrationSourceAdapter(val items: List<MigrationSourceItem>,
                             val controller: MigrationDesignController): FlexibleAdapter<MigrationSourceItem>(
        items,
        controller,
        true
) {
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putParcelableArrayList(SELECTED_SOURCES_KEY, ArrayList(currentItems.map {
            it.asParcelable()
        }))
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        savedInstanceState.getParcelableArrayList<MigrationSourceItem.ParcelableSI>(SELECTED_SOURCES_KEY)?.let {
            updateDataSet(it.map { MigrationSourceItem.fromParcelable(sourceManager, it) })
        }

        super.onRestoreInstanceState(savedInstanceState)
    }

    companion object {
        private const val SELECTED_SOURCES_KEY = "selected_sources"
    }
}