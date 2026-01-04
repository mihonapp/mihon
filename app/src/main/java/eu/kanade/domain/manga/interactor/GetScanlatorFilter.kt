package eu.kanade.domain.manga.interactor

import eu.kanade.domain.manga.model.ScanlatorFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tachiyomi.data.DatabaseHandler

class GetScanlatorFilter(
    private val handler: DatabaseHandler,
) {

    suspend fun await(mangaId: Long): List<ScanlatorFilter> {
        return handler.awaitList {
            scanlator_filterQueries.getScanlatorFilterByMangaId(mangaId)
        }
            .map { ScanlatorFilter(it.scanlator, it.priority.toInt()) }
    }

    fun subscribe(mangaId: Long): Flow<List<ScanlatorFilter>> {
        return handler.subscribeToList {
            scanlator_filterQueries.getScanlatorFilterByMangaId(mangaId)
        }
            .map { list ->
                list.map { ScanlatorFilter(it.scanlator, it.priority.toInt()) }
            }
    }
}
