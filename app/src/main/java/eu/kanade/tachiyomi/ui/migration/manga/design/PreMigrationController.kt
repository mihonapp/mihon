package eu.kanade.tachiyomi.ui.migration.manga.design

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import com.bluelinelabs.conductor.Router
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.databinding.PreMigrationControllerBinding
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.base.controller.BaseController
import eu.kanade.tachiyomi.ui.base.controller.withFadeTransaction
import eu.kanade.tachiyomi.ui.migration.manga.process.MigrationListController
import eu.kanade.tachiyomi.ui.migration.manga.process.MigrationProcedureConfig
import exh.util.applyWindowInsetsForController
import exh.util.doOnApplyWindowInsets
import exh.util.marginBottom
import exh.util.updateLayoutParams
import exh.util.updatePaddingRelative
import uy.kohesive.injekt.injectLazy

class PreMigrationController(bundle: Bundle? = null) : BaseController<PreMigrationControllerBinding>(bundle), FlexibleAdapter
.OnItemClickListener, StartMigrationListener {
    private val sourceManager: SourceManager by injectLazy()
    private val prefs: PreferencesHelper by injectLazy()

    private var adapter: MigrationSourceAdapter? = null

    private val config: LongArray = args.getLongArray(MANGA_IDS_EXTRA) ?: LongArray(0)

    private var showingOptions = false

    private var dialog: BottomSheetDialog? = null

    override fun getTitle() = "Select target sources"

    override fun inflateView(inflater: LayoutInflater, container: ViewGroup): View {
        binding = PreMigrationControllerBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)
        view.applyWindowInsetsForController()

        val ourAdapter = adapter ?: MigrationSourceAdapter(
            getEnabledSources().map { MigrationSourceItem(it, isEnabled(it.id.toString())) },
            this
        )
        adapter = ourAdapter
        binding.recycler.layoutManager = LinearLayoutManager(view.context)
        binding.recycler.setHasFixedSize(true)
        binding.recycler.adapter = ourAdapter
        ourAdapter.itemTouchHelperCallback = null // Reset adapter touch adapter to fix drag after rotation
        ourAdapter.isHandleDragEnabled = true
        dialog = null
        val fabBaseMarginBottom = binding.fab.marginBottom
        binding.recycler.doOnApplyWindowInsets { v, insets, padding ->

            binding.fab.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = fabBaseMarginBottom + insets.systemWindowInsetBottom
            }
            // offset the recycler by the fab's inset + some inset on top
            v.updatePaddingRelative(bottom = padding.bottom + (binding.fab.marginBottom) +
                fabBaseMarginBottom + (binding.fab.height))
        }

        binding.fab.setOnClickListener {
            if (dialog?.isShowing != true) {
                dialog = MigrationBottomSheetDialog(activity!!, R.style.SheetDialog, this)
                dialog?.show()
                val bottomSheet = dialog?.findViewById<FrameLayout>(
                    com.google.android.material.R.id.design_bottom_sheet
                )
                if (bottomSheet != null) {
                    val behavior: BottomSheetBehavior<*> = BottomSheetBehavior.from(bottomSheet)
                    behavior.state = BottomSheetBehavior.STATE_EXPANDED
                    behavior.skipCollapsed = true
                }
            }
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
            ).withFadeTransaction().tag(MigrationListController.TAG))
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
        var sources = sourceManager.getVisibleCatalogueSources()
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

        fun navigateToMigration(skipPre: Boolean, router: Router, mangaIds: List<Long>) {
            router.pushController(
                if (skipPre) {
                    MigrationListController.create(
                        MigrationProcedureConfig(mangaIds, null)
                    )
                } else {
                    create(mangaIds)
                }.withFadeTransaction().tag(if (skipPre) MigrationListController.TAG else null)
            )
        }

        fun create(mangaIds: List<Long>): PreMigrationController {
            return PreMigrationController(Bundle().apply {
                putLongArray(MANGA_IDS_EXTRA, mangaIds.toLongArray())
            })
        }
    }
}
