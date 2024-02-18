package eu.kanade.tachiyomi.ui.updates.calendar

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.core.util.insertSeparators
import eu.kanade.presentation.updates.UpcomingUIModel
import eu.kanade.tachiyomi.util.lang.toLocalDate
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.manga.interactor.GetUpcomingManga
import tachiyomi.domain.manga.model.Manga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.LocalDate


class UpdateCalendarScreenModel(
    private val getUpcomingManga: GetUpcomingManga = Injekt.get(),
) : StateScreenModel<UpdateCalendarScreenModel.State>(State()) {

    init {
        screenModelScope.launch {
            val manga = getUpcomingManga.await()
            val items = manga.toUpcomingUIModels()
            val events = manga.toEvents()
            mutableState.update {
                it.copy(
                    items = items,
                    events = events,
                )
            }

        }
    }

    private fun List<Manga>.toUpcomingUIModels(): List<UpcomingUIModel> {
        return mapIndexed { i, item -> UpcomingUIModel.Item(item) }
            .insertSeparators { before, after ->
                val beforeDate = before?.item?.expectedNextUpdate?.toLocalDate() ?: LocalDate.MAX
                val afterDate = after?.item?.expectedNextUpdate?.toLocalDate() ?: LocalDate.MAX
                when {
                    beforeDate.isBefore(afterDate)
                        or beforeDate.equals(LocalDate.MAX)
                        and !afterDate.equals(LocalDate.MAX)
                    -> UpcomingUIModel.Header(afterDate)
                    // Return null to avoid adding a separator between two items.
                    else -> null
                }
            }
    }

    private fun List<Manga>.toEvents(): Map<LocalDate, Int> {
        return groupBy { it.expectedNextUpdate?.toLocalDate() ?: LocalDate.MAX }
            .mapValues { it.value.size }
    }

    data class State(
        val items: List<UpcomingUIModel> = persistentListOf(),
        val events: Map<LocalDate, Int> = persistentMapOf(),
        val headerIndexes: Map<LocalDate, Int> = persistentMapOf(),
    )
}
