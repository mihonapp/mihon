package eu.kanade.domain.source.interactor

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.source.isNovelSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import tachiyomi.core.common.util.lang.compareToWithCollator
import tachiyomi.domain.source.model.Source
import tachiyomi.domain.source.repository.SourceRepository
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.source.local.isLocal
import java.util.Collections

class GetNovelSourcesWithFavoriteCount(
    private val repository: SourceRepository,
    private val preferences: SourcePreferences,
    private val sourceManager: SourceManager,
) {

    fun subscribe(): Flow<List<Pair<Source, Long>>> {
        return combine(
            preferences.migrationSortingDirection().changes(),
            preferences.migrationSortingMode().changes(),
            repository.getSourcesWithFavoriteCount(),
        ) { direction, mode, list ->
            list
                .filter { (source, _) -> 
                    val actualSource = sourceManager.get(source.id)
                    !source.isLocal() && actualSource?.isNovelSource() == true
                }
                .sortedWith(sortFn(direction, mode))
        }
    }

    private fun sortFn(
        direction: SetMigrateSorting.Direction,
        sorting: SetMigrateSorting.Mode,
    ): java.util.Comparator<Pair<Source, Long>> {
        val sortFn: (Pair<Source, Long>, Pair<Source, Long>) -> Int = { a, b ->
            when (sorting) {
                SetMigrateSorting.Mode.ALPHABETICAL -> {
                    when {
                        a.first.isStub && !b.first.isStub -> -1
                        b.first.isStub && !a.first.isStub -> 1
                        else -> a.first.name.lowercase().compareToWithCollator(b.first.name.lowercase())
                    }
                }
                SetMigrateSorting.Mode.TOTAL -> {
                    when {
                        a.first.isStub && !b.first.isStub -> -1
                        b.first.isStub && !a.first.isStub -> 1
                        else -> a.second.compareTo(b.second)
                    }
                }
            }
        }

        return when (direction) {
            SetMigrateSorting.Direction.ASCENDING -> Comparator(sortFn)
            SetMigrateSorting.Direction.DESCENDING -> Collections.reverseOrder(sortFn)
        }
    }
}
