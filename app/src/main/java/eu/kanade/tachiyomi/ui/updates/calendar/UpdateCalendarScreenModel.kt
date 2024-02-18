package eu.kanade.tachiyomi.ui.updates.calendar

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.core.util.insertSeparators
import eu.kanade.presentation.updates.UpcomingUIModel
import eu.kanade.tachiyomi.util.lang.toLocalDate
import kotlinx.collections.immutable.persistentListOf
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
            mutableState.update {
                it.copy(items = getUpcomingManga.await().toUpcomingUIModels())
            }

        }
    }

    private fun List<Manga>.toUpcomingUIModels(): List<UpcomingUIModel> {
        return map { UpcomingUIModel.Item(it) }
            .insertSeparators { before, after ->
                val beforeDate = before?.item?.expectedNextUpdate?.toLocalDate() ?: LocalDate.MAX
                val afterDate = after?.item?.expectedNextUpdate?.toLocalDate() ?: LocalDate.MAX
                when {
                    (beforeDate.isBefore(afterDate)
                        or afterDate.equals(LocalDate.MAX)
                        or beforeDate.equals(LocalDate.MAX))
                        and (!afterDate.equals(LocalDate.MIN) or !beforeDate.equals(LocalDate.MAX))
                    -> UpcomingUIModel.Header(afterDate)
                    // Return null to avoid adding a separator between two items.
                    else -> null
                }
            }
    }

    data class State(
        val items: List<UpcomingUIModel> = persistentListOf(),
    )
}
