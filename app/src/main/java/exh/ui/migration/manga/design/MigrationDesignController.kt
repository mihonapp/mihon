package exh.ui.migration.manga.design

import android.support.v7.widget.LinearLayoutManager
import android.view.View
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.base.controller.withFadeTransaction
import exh.ui.base.BaseExhController
import exh.ui.migration.manga.process.MigrationProcedureController
import kotlinx.android.synthetic.main.eh_migration_design.*
import uy.kohesive.injekt.injectLazy

// TODO Handle config changes
// TODO Select all in library
class MigrationDesignController : BaseExhController(), FlexibleAdapter.OnItemClickListener {
    private val sourceManager: SourceManager by injectLazy()
    private val prefs: PreferencesHelper by injectLazy()

    override val layoutId: Int = R.layout.eh_migration_design

    private var adapter: FlexibleAdapter<MigrationSourceItem>? = null

    override fun getTitle() = "Select target sources"

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)

        adapter = MigrationSourceAdapter(
                getEnabledSources().map { MigrationSourceItem(it, true) },
                this
        )
        recycler.layoutManager = LinearLayoutManager(view.context)
        recycler.setHasFixedSize(true)
        recycler.adapter = adapter
        adapter?.isHandleDragEnabled = true

        migration_mode.setOnClickListener {
            prioritize_chapter_count.toggle()
        }

        fuzzy_search.setOnClickListener {
            use_smart_search.toggle()
        }

        prioritize_chapter_count.setOnCheckedChangeListener { _, b ->
            updatePrioritizeChapterCount(b)
        }

        updatePrioritizeChapterCount(prioritize_chapter_count.isChecked)

        begin_migration_btn.setOnClickListener {
            router.replaceTopController(MigrationProcedureController().withFadeTransaction())
        }
    }

    private fun updatePrioritizeChapterCount(migrationMode: Boolean) {
        migration_mode.text = if(migrationMode) {
            "Use source with most chapters and use the above list to break ties"
        } else {
            "Use the first source in the list that has at least one chapter of the manga"
        }
    }

    override fun onItemClick(view: View, position: Int): Boolean {
        adapter?.getItem(position)?.let {
            it.sourceEnabled = !it.sourceEnabled
        }
        adapter?.notifyItemChanged(position)
        return false
    }

    /**
     * Returns a list of enabled sources ordered by language and name.
     *
     * @return list containing enabled sources.
     */
    private fun getEnabledSources(): List<HttpSource> {
        val languages = prefs.enabledLanguages().getOrDefault()
        val hiddenCatalogues = prefs.hiddenCatalogues().getOrDefault()

        return sourceManager.getVisibleCatalogueSources()
                .filterIsInstance<HttpSource>()
                .filter { it.lang in languages }
                .filterNot { it.id.toString() in hiddenCatalogues }
                .sortedBy { "(${it.lang}) ${it.name}" }
    }
}