package eu.kanade.tachiyomi.ui.smartsearch

import android.os.Bundle
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.smartsearch.SmartSearchEngine
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.ui.source.SourceController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

class SmartSearchPresenter(private val source: CatalogueSource?, private val config: SourceController.SmartSearchConfig?) :
        BasePresenter<SmartSearchController>(), CoroutineScope {

    override val coroutineContext = Job() + Dispatchers.Main

    val smartSearchChannel = Channel<SearchResults>()

    private val smartSearchEngine = SmartSearchEngine(coroutineContext)

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        if (source != null && config != null) {
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
        data class Found(val manga: Manga) : SearchResults()
        object NotFound : SearchResults()
        object Error : SearchResults()
    }
}
