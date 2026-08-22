package eu.kanade.domain.manga.interactor

import dev.zacsweers.metro.Inject
import eu.kanade.domain.manga.model.ScanlatorFilter
import tachiyomi.data.Database

@Inject
class SetScanlatorFilter(
    private val database: Database,
) {

    suspend fun await(mangaId: Long, filters: List<ScanlatorFilter>) {
        database.transaction {
            database.scanlator_filterQueries.deleteForManga(mangaId)
            filters.forEach {
                val scanlator = it.scanlator?.takeUnless { s -> s.isEmpty() }
                database.scanlator_filterQueries.insert(
                    mangaId,
                    scanlator,
                    it.priority.toLong(),
                    if (it.excluded) 1L else 0L,
                )
            }
        }
    }
}
