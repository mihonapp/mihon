package eu.kanade.domain.manga.interactor

import app.cash.sqldelight.async.coroutines.awaitAsList
import tachiyomi.data.Database

class SetExcludedScanlators(
    private val database: Database,
) {

    suspend fun await(mangaId: Long, excludedScanlators: Set<String>) {
        database.transaction {
            val currentExcluded = database.excluded_scanlatorsQueries
                .getExcludedScanlatorsByMangaId(mangaId)
                .awaitAsList()
                .toSet()
            val toAdd = excludedScanlators.minus(currentExcluded)
            for (scanlator in toAdd) {
                database.excluded_scanlatorsQueries.insert(mangaId, scanlator)
            }
            val toRemove = currentExcluded.minus(excludedScanlators)
            database.excluded_scanlatorsQueries.remove(mangaId, toRemove)
        }
    }
}
