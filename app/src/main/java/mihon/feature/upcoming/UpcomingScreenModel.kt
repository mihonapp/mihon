package mihon.feature.upcoming

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.core.util.insertSeparators
import eu.kanade.tachiyomi.util.lang.toLocalDate
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mihon.domain.upcoming.interactor.GetUpcomingManga
import tachiyomi.domain.manga.model.Manga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.LocalDate

class UpcomingScreenModel(
    private val getUpcomingManga: GetUpcomingManga = Injekt.get(),
) : StateScreenModel<UpcomingScreenModel.State>(State()) {

    init {
        screenModelScope.launch {
            getUpcomingManga.subscribe().collectLatest {
                mutableState.update { state ->
                    val upcomingItems = it.toUpcomingUIModels()
                    state.copy(
                        items = upcomingItems,
                        events = it.toEvents(),
                        headerIndexes = getHeaderIndexes(upcomingItems),
                    )
                }
            }
        }
    }

    private fun List<Manga>.toUpcomingUIModels(): ImmutableList<UpcomingUIModel> {
        return map { UpcomingUIModel.Item(it) }
            .insertSeparators { before, after ->
                val beforeDate = before?.manga?.expectedNextUpdate?.toLocalDate()
                val afterDate = after?.manga?.expectedNextUpdate?.toLocalDate()
                when {
                    beforeDate != afterDate && afterDate != null -> UpcomingUIModel.Header(afterDate)
                    // Return null to avoid adding a separator between two items.
                    else -> null
                }
            }
            .toImmutableList()
    }

    private fun List<Manga>.toEvents(): ImmutableMap<LocalDate, Int> {
        return groupBy { it.expectedNextUpdate?.toLocalDate() ?: LocalDate.MAX }
            .mapValues { it.value.size }
            .toImmutableMap()
    }

    private fun getHeaderIndexes(upcomingItems: List<UpcomingUIModel>): ImmutableMap<LocalDate, Int> {
        return upcomingItems.withIndex()
            .filter { it.value is UpcomingUIModel.Header }
            .associate { Pair((it.value as UpcomingUIModel.Header).date, it.index) }
            .toImmutableMap()
    }

    data class State(
        val items: ImmutableList<UpcomingUIModel> = persistentListOf(),
        val events: ImmutableMap<LocalDate, Int> = persistentMapOf(),
        val headerIndexes: ImmutableMap<LocalDate, Int> = persistentMapOf(),
    )
}
