package exh.ui.smartsearch

import android.os.Bundle
import com.elvishew.xlog.XLog
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.ui.catalogue.CatalogueController
import exh.smartsearch.SmartSearchEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

class SmartSearchPresenter(private val source: CatalogueSource?, private val config: CatalogueController.SmartSearchConfig?):
        BasePresenter<SmartSearchController>(), CoroutineScope {
    private val logger = XLog.tag("SmartSearchPresenter")

    override val coroutineContext = Job() + Dispatchers.Main

    val smartSearchChannel = Channel<SearchResults>()

    private val smartSearchEngine = SmartSearchEngine(coroutineContext)

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        if(source != null && config != null) {
            launch(Dispatchers.Default) {
                val result = try {
                    val resultManga = smartSearchEngine.smartSearch(source, config.origTitle)
                    if (resultManga != null) {
                        val localManga = smartSearchEngine.networkToLocalManga(resultManga, source.id)
                        SearchResults.Found(localManga)
                    } else {
                        SearchResults.NotFound
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) {
                        throw e
                    } else {
                        logger.e("Smart search error", e)
                        SearchResults.Error
                    }
                }

                smartSearchChannel.send(result)
            }
        }
    }


    override fun onDestroy() {
        super.onDestroy()

        cancel()
    }

    data class SearchEntry(val manga: SManga, val dist: Double)

    sealed class SearchResults {
        data class Found(val manga: Manga): SearchResults()
        object NotFound: SearchResults()
        object Error: SearchResults()
    }
}