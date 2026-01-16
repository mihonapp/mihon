package eu.kanade.domain.manga.interactor

import eu.kanade.domain.manga.model.ScanlatorFilter
import tachiyomi.data.DatabaseHandler

class SetScanlatorFilter(
    private val handler: DatabaseHandler,
) {

    suspend fun await(mangaId: Long, filters: List<ScanlatorFilter>) {
        handler.await(inTransaction = true) {
            scanlator_filterQueries.deleteForManga(mangaId)
            filters.forEach {
                val scanlator = it.scanlator?.takeUnless { s -> s.isEmpty() }
                scanlator_filterQueries.insert(
                    mangaId,
                    scanlator,
                    it.priority.toLong(),
                    if (it.excluded) 1L else 0L,
                )
            }
        }
    }
}
