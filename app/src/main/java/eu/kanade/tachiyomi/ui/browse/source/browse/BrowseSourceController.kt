package eu.kanade.tachiyomi.ui.browse.source.browse

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.core.os.bundleOf
import cafe.adriel.voyager.navigator.CurrentScreen
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.tachiyomi.ui.base.controller.BasicFullComposeController
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch

class BrowseSourceController(bundle: Bundle) : BasicFullComposeController(bundle) {

    constructor(sourceId: Long, query: String? = null) : this(
        bundleOf(
            SOURCE_ID_KEY to sourceId,
            SEARCH_QUERY_KEY to query,
        ),
    )

    private val sourceId = args.getLong(SOURCE_ID_KEY)
    private val initialQuery = args.getString(SEARCH_QUERY_KEY)

    private val queryEvent = Channel<BrowseSourceScreen.SearchType>()

    @Composable
    override fun ComposeContent() {
        Navigator(screen = BrowseSourceScreen(sourceId = sourceId, query = initialQuery)) { navigator ->
            CurrentScreen()

            LaunchedEffect(Unit) {
                queryEvent.consumeAsFlow()
                    .collectLatest {
                        val screen = (navigator.lastItem as? BrowseSourceScreen)
                        when (it) {
                            is BrowseSourceScreen.SearchType.Genre -> screen?.searchGenre(it.txt)
                            is BrowseSourceScreen.SearchType.Text -> screen?.search(it.txt)
                        }
                    }
            }
        }
    }

    /**
     * Restarts the request with a new query.
     *
     * @param newQuery the new query.
     */
    fun searchWithQuery(newQuery: String) {
        viewScope.launch { queryEvent.send(BrowseSourceScreen.SearchType.Text(newQuery)) }
    }

    /**
     * Attempts to restart the request with a new genre-filtered query.
     * If the genre name can't be found the filters,
     * the standard searchWithQuery search method is used instead.
     *
     * @param genreName the name of the genre
     */
    fun searchWithGenre(genreName: String) {
        viewScope.launch { queryEvent.send(BrowseSourceScreen.SearchType.Genre(genreName)) }
    }
}

private const val SOURCE_ID_KEY = "sourceId"
private const val SEARCH_QUERY_KEY = "searchQuery"
