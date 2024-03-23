package eu.kanade.tachiyomi.ui.updates

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.core.util.insertSeparators
import eu.kanade.presentation.updates.UpcomingUIModel
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
import tachiyomi.domain.manga.interactor.GetUpcomingManga
import tachiyomi.domain.manga.model.Manga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.LocalDate

class UpdateUpcomingScreenModel(
    private val getUpcomingManga: GetUpcomingManga = Injekt.get(),
) : StateScreenModel<UpdateUpcomingScreenModel.State>(State()) {

    init {
        screenModelScope.launch {
            getUpcomingManga.subscribe().collectLatest {
                val items = it.toUpcomingUIModels()
                val events = it.toEvents()
                mutableState.update { state ->
                    state.copy(
                        items = items,
                        events = events,
                    )
                }
            }
        }
    }

    private fun List<Manga>.toUpcomingUIModels(): ImmutableList<UpcomingUIModel> {
        return map { UpcomingUIModel.Item(it) }
            .insertSeparators { before, after ->
                val beforeDate = before?.item?.expectedNextUpdate?.toLocalDate()
                val afterDate = after?.item?.expectedNextUpdate?.toLocalDate()
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

    data class State(
        val items: ImmutableList<UpcomingUIModel> = persistentListOf(),
        val events: ImmutableMap<LocalDate, Int> = persistentMapOf(),
        val headerIndexes: Map<LocalDate, Int> = persistentMapOf(),
    )
}