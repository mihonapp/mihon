package exh.ui.migration.manga.design

import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.view.View
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.base.controller.withFadeTransaction
import eu.kanade.tachiyomi.ui.migration.MigrationFlags
import eu.kanade.tachiyomi.util.gone
import eu.kanade.tachiyomi.util.visible
import exh.ui.base.BaseExhController
import exh.ui.migration.manga.process.MigrationProcedureConfig
import exh.ui.migration.manga.process.MigrationProcedureController
import kotlinx.android.synthetic.main.eh_migration_design.*
import uy.kohesive.injekt.injectLazy

// TODO Select all in library
class MigrationDesignController(bundle: Bundle? = null) : BaseExhController(bundle), FlexibleAdapter.OnItemClickListener {
    private val sourceManager: SourceManager by injectLazy()
    private val prefs: PreferencesHelper by injectLazy()

    override val layoutId: Int = R.layout.eh_migration_design

    private var adapter: MigrationSourceAdapter? = null

    private val config: LongArray = args.getLongArray(MANGA_IDS_EXTRA) ?: LongArray(0)

    private var showingOptions = false

    override fun getTitle() = "Select target sources"

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)

        val ourAdapter = adapter ?: MigrationSourceAdapter(
                getEnabledSources().map { MigrationSourceItem(it, true) },
                this
        )
        adapter = ourAdapter
        recycler.layoutManager = LinearLayoutManager(view.context)
        recycler.setHasFixedSize(true)
        recycler.adapter = ourAdapter
        ourAdapter.itemTouchHelperCallback = null // Reset adapter touch adapter to fix drag after rotation
        ourAdapter.isHandleDragEnabled = true

        migration_mode.setOnClickListener {
            prioritize_chapter_count.toggle()
        }

        fuzzy_search.setOnClickListener {
            use_smart_search.toggle()
        }

        copy_manga_desc.setOnClickListener {
            copy_manga.toggle()
        }

        extra_search_param_desc.setOnClickListener {
            extra_search_param.toggle()
        }

        prioritize_chapter_count.setOnCheckedChangeListener { _, b ->
            updatePrioritizeChapterCount(b)
        }

        extra_search_param.setOnCheckedChangeListener { _, b ->
            updateOptionsState()
        }

        updatePrioritizeChapterCount(prioritize_chapter_count.isChecked)

        updateOptionsState()

        begin_migration_btn.setOnClickListener {
            if(!showingOptions) {
                showingOptions = true
                updateOptionsState()
                return@setOnClickListener
            }

            var flags = 0
            if(mig_chapters.isChecked) flags = flags or MigrationFlags.CHAPTERS
            if(mig_categories.isChecked) flags = flags or MigrationFlags.CATEGORIES
            if(mig_categories.isChecked) flags = flags or MigrationFlags.TRACK

            router.replaceTopController(MigrationProcedureController.create(
                    MigrationProcedureConfig(
                            config.toList(),
                            ourAdapter.items.filter {
                                it.sourceEnabled
                            }.map { it.source.id },
                            useSourceWithMostChapters = prioritize_chapter_count.isChecked,
                            enableLenientSearch = use_smart_search.isChecked,
                            migrationFlags = flags,
                            copy = copy_manga.isChecked,
                            extraSearchParams = if(extra_search_param.isChecked && extra_search_param_text.text.isNotBlank()) {
                                extra_search_param_text.text.toString()
                            } else null
                    )
            ).withFadeTransaction())
        }
    }

    fun updateOptionsState() {
        if (showingOptions) {
            begin_migration_btn.text = "Begin migration"
            options_group.visible()
            if(extra_search_param.isChecked) {
                extra_search_param_text.visible()
            } else {
                extra_search_param_text.gone()
            }
        } else {
            begin_migration_btn.text = "Next step"
            options_group.gone()
            extra_search_param_text.gone()
        }
    }

    override fun handleBack(): Boolean {
        if(showingOptions) {
            showingOptions = false
            updateOptionsState()
            return true
        }
        return super.handleBack()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        adapter?.onSaveInstanceState(outState)
    }

    // TODO Still incorrect, why is this called before onViewCreated?
    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        adapter?.onRestoreInstanceState(savedInstanceState)
    }

    private fun updatePrioritizeChapterCount(migrationMode: Boolean) {
        migration_mode.text = if(migrationMode) {
            "Currently using the source with the most chapters and the above list to break ties (slow with many sources or smart search)"
        } else {
            "Currently using the first source in the list that has the manga"
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

    companion object {
        private const val MANGA_IDS_EXTRA = "manga_ids"

        fun create(mangaIds: List<Long>): MigrationDesignController {
            return MigrationDesignController(Bundle().apply {
                putLongArray(MANGA_IDS_EXTRA, mangaIds.toLongArray())
            })
        }
    }
}