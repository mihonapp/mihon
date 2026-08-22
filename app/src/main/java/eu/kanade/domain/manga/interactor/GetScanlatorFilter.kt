package eu.kanade.domain.manga.interactor

import app.cash.sqldelight.async.coroutines.awaitAsList
import dev.zacsweers.metro.Inject
import eu.kanade.domain.manga.model.ScanlatorFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tachiyomi.data.Database
import tachiyomi.data.subscribeToList

@Inject
class GetScanlatorFilter(
    private val database: Database,
) {

    suspend fun await(mangaId: Long): List<ScanlatorFilter> {
        return database.scanlator_filterQueries
            .getScanlatorFilterByMangaId(mangaId)
            .awaitAsList()
            .map { ScanlatorFilter(it.scanlator, it.priority.toInt(), it.excluded == 1L) }
    }

    fun subscribe(mangaId: Long): Flow<List<ScanlatorFilter>> {
        return database.scanlator_filterQueries
            .getScanlatorFilterByMangaId(mangaId)
            .subscribeToList()
            .map { list -> list.map { ScanlatorFilter(it.scanlator, it.priority.toInt(), it.excluded == 1L) } }
    }
}
