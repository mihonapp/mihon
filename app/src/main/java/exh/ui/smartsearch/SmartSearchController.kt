package exh.ui.smartsearch

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.base.controller.NucleusController
import eu.kanade.tachiyomi.ui.base.controller.withFadeTransaction
import eu.kanade.tachiyomi.ui.catalogue.CatalogueController
import eu.kanade.tachiyomi.ui.catalogue.browse.BrowseCatalogueController
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.util.toast
import kotlinx.android.synthetic.main.eh_smart_search.*
import kotlinx.coroutines.*
import uy.kohesive.injekt.injectLazy

class SmartSearchController(bundle: Bundle? = null) : NucleusController<SmartSearchPresenter>(), CoroutineScope {
    override val coroutineContext = Job() + Dispatchers.Main

    private val sourceManager: SourceManager by injectLazy()

    private val source = sourceManager.get(bundle?.getLong(ARG_SOURCE_ID, -1) ?: -1) as? CatalogueSource
    private val smartSearchConfig: CatalogueController.SmartSearchConfig? = bundle?.getParcelable(ARG_SMART_SEARCH_CONFIG)

    override fun inflateView(inflater: LayoutInflater, container: ViewGroup) =
            inflater.inflate(R.layout.eh_smart_search, container, false)!!

    override fun getTitle() = source?.name ?: ""

    override fun createPresenter() = SmartSearchPresenter(source, smartSearchConfig)

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)

        appbar.bringToFront()

        if(source == null || smartSearchConfig == null) {
            router.popCurrentController()
            applicationContext?.toast("Missing data!")
            return
        }

        // Init presenter now to resolve threading issues
        presenter

        launch(Dispatchers.Default) {
            for(event in presenter.smartSearchChannel) {
                withContext(NonCancellable) {
                    if (event is SmartSearchPresenter.SearchResults.Found) {
                        val transaction = MangaController(event.manga, true, smartSearchConfig).withFadeTransaction()
                        withContext(Dispatchers.Main) {
                            router.replaceTopController(transaction)
                        }
                    } else {
                        if (event is SmartSearchPresenter.SearchResults.NotFound) {
                            applicationContext?.toast("Couldn't find the manga in the source!")
                        } else {
                            applicationContext?.toast("Error performing automatic search!")
                        }

                        val transaction = BrowseCatalogueController(source, smartSearchConfig.origTitle, smartSearchConfig).withFadeTransaction()
                        withContext(Dispatchers.Main) {
                            router.replaceTopController(transaction)
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        cancel()
    }

    companion object {
        const val ARG_SOURCE_ID = "SOURCE_ID"
        const val ARG_SMART_SEARCH_CONFIG = "SMART_SEARCH_CONFIG"
    }
}