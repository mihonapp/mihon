package eu.kanade.domain.manga.interactor

import app.cash.sqldelight.async.coroutines.awaitAsList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tachiyomi.data.Database
import tachiyomi.data.subscribeToList

class GetExcludedScanlators(
    private val database: Database,
) {

    suspend fun await(mangaId: Long): Set<String> {
        return database.excluded_scanlatorsQueries
            .getExcludedScanlatorsByMangaId(mangaId)
            .awaitAsList()
            .toSet()
    }

    fun subscribe(mangaId: Long): Flow<Set<String>> {
        return database.excluded_scanlatorsQueries
            .getExcludedScanlatorsByMangaId(mangaId)
            .subscribeToList()
            .map { it.toSet() }
    }
}
