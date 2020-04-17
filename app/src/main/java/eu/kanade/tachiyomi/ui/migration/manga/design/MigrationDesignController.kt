package eu.kanade.tachiyomi.ui.migration.manga.design

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.base.controller.BaseController
import eu.kanade.tachiyomi.ui.base.controller.withFadeTransaction
import eu.kanade.tachiyomi.ui.migration.manga.process.MigrationListController
import eu.kanade.tachiyomi.ui.migration.manga.process.MigrationProcedureConfig
import eu.kanade.tachiyomi.util.view.doOnApplyWindowInsets
import eu.kanade.tachiyomi.util.view.marginBottom
import eu.kanade.tachiyomi.util.view.updateLayoutParams
import eu.kanade.tachiyomi.util.view.updatePaddingRelative
import kotlinx.android.synthetic.main.migration_design_controller.fab
import kotlinx.android.synthetic.main.migration_design_controller.recycler
import uy.kohesive.injekt.injectLazy

class MigrationDesignController(bundle: Bundle? = null) : BaseController(bundle), FlexibleAdapter
.OnItemClickListener, StartMigrationListener {
    private val sourceManager: SourceManager by injectLazy()
    private val prefs: PreferencesHelper by injectLazy()

    private var adapter: MigrationSourceAdapter? = null

    private val config: LongArray = args.getLongArray(MANGA_IDS_EXTRA) ?: LongArray(0)

    private var showingOptions = false

    override fun getTitle() = "Select target sources"

    override fun inflateView(inflater: LayoutInflater, container: ViewGroup): View {
        return inflater.inflate(R.layout.migration_design_controller, container, false)
    }

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)

        val ourAdapter = adapter ?: MigrationSourceAdapter(
                getEnabledSources().map { MigrationSourceItem(it, isEnabled(it.id.toString())) },
                this
        )
        adapter = ourAdapter
        recycler.layoutManager = LinearLayoutManager(view.context)
        recycler.setHasFixedSize(true)
        recycler.adapter = ourAdapter
        ourAdapter.itemTouchHelperCallback = null // Reset adapter touch adapter to fix drag after rotation
        ourAdapter.isHandleDragEnabled = true

        val fabBaseMarginBottom = fab?.marginBottom ?: 0
        recycler.doOnApplyWindowInsets { v, insets, padding ->

            fab?.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = fabBaseMarginBottom + insets.systemWindowInsetBottom
            }
            // offset the recycler by the fab's inset + some inset on top
            v.updatePaddingRelative(bottom = padding.bottom + (fab?.marginBottom ?: 0) +
                fabBaseMarginBottom + (fab?.height ?: 0))
        }

        fab.setOnClickListener {
            val dialog = MigrationBottomSheetDialog(activity!!, R.style.SheetDialog, this)
            dialog.show()
            val bottomSheet =
                dialog.findViewById<FrameLayout>(com.google.android.material.R.id
                    .design_bottom_sheet)
            val behavior: BottomSheetBehavior<*> = BottomSheetBehavior.from(bottomSheet!!)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
        }
    }

    override fun startMigration(extraParam: String?) {
        val listOfSources = adapter?.items?.filter {
            it.sourceEnabled
        }?.joinToString("/") { it.source.id.toString() }
        prefs.migrationSources().set(listOfSources)

        router.replaceTopController(
            MigrationListController.create(
                MigrationProcedureConfig(
                    config.toList(),
                    extraSearchParams = extraParam
                )
            ).withFadeTransaction())
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
        val sourcesSaved = prefs.migrationSources().getOrDefault().split("/")
        var sources = sourceManager.getCatalogueSources()
            .filterIsInstance<HttpSource>()
            .filter { it.lang in languages }
            .sortedBy { "(${it.lang}) ${it.name}" }
        sources =
            sources.filter { isEnabled(it.id.toString()) }.sortedBy { sourcesSaved.indexOf(it.id
                .toString())
            } +
                sources.filterNot { isEnabled(it.id.toString()) }

        return sources
    }

    fun isEnabled(id: String): Boolean {
        val sourcesSaved = prefs.migrationSources().getOrDefault()
        val hiddenCatalogues = prefs.hiddenCatalogues().getOrDefault()
        return if (sourcesSaved.isEmpty()) id !in hiddenCatalogues
        else sourcesSaved.split("/").contains(id)
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
