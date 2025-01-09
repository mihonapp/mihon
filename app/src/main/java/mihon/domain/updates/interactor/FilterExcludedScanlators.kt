package mihon.domain.updates.interactor

import eu.kanade.domain.manga.interactor.GetExcludedScanlators
import tachiyomi.domain.updates.model.UpdatesWithRelations

/**
 * Filters a list of manga updates to exclude those with scanlators marked as excluded.
 *
 * @property getExcludedScanlators Fetches the excluded scanlators for a manga ID.
 */
class FilterExcludedScanlators(
    private val getExcludedScanlators: GetExcludedScanlators,
) {

    /**
     * Filters updates to exclude those with scanlators in the excluded list for their manga.
     *
     * @param updates The list of manga updates to filter.
     * @return A filtered list of updates.
     */
    suspend fun await(updates: List<UpdatesWithRelations>): List<UpdatesWithRelations> {
        val excludedScanlatorsCache = mutableMapOf<Long, Set<String>>()

        return updates.filter {
            val excludedScanlators = excludedScanlatorsCache.getOrPut(it.mangaId) {
                getExcludedScanlators.await(it.mangaId)
            }

            it.scanlator !in excludedScanlators
        }
    }
}
