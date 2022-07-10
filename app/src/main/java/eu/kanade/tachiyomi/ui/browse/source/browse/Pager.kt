package eu.kanade.tachiyomi.ui.browse.source.browse

import com.jakewharton.rxrelay.PublishRelay
import eu.kanade.core.util.asFlow
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.flow.Flow

/**
 * A general pager for source requests (latest updates, popular, search)
 */
abstract class Pager(var currentPage: Int = 1) {

    var hasNextPage = true
        private set

    protected val results: PublishRelay<Pair<Int, List<SManga>>> = PublishRelay.create()

    fun asFlow(): Flow<Pair<Int, List<SManga>>> {
        return results.asObservable().asFlow()
    }

    abstract suspend fun requestNextPage()

    fun onPageReceived(mangasPage: MangasPage) {
        val page = currentPage
        currentPage++
        hasNextPage = mangasPage.hasNextPage && mangasPage.mangas.isNotEmpty()
        results.call(Pair(page, mangasPage.mangas))
    }
}
