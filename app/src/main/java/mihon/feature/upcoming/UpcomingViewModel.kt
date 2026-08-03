package mihon.feature.upcoming

import androidx.compose.ui.util.fastMap
import androidx.compose.ui.util.fastMapIndexedNotNull
import androidx.lifecycle.viewModelScope
import eu.kanade.core.util.insertSeparatorsReversed
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearMonth
import mihon.core.viewmodel.StateViewModel
import mihon.domain.upcoming.interactor.GetUpcomingManga
import tachiyomi.domain.manga.model.Manga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Clock

class UpcomingViewModel(
    private val getUpcomingManga: GetUpcomingManga = Injekt.get(),
) : StateViewModel<UpcomingViewModel.State>(State()) {

    init {
        viewModelScope.launch {
            getUpcomingManga.subscribe().collectLatest {
                mutableState.update { state ->
                    val upcomingItems = it.toUpcomingUIModels()
                    state.copy(
                        items = upcomingItems,
                        events = upcomingItems.toEvents(),
                        headerIndexes = upcomingItems.getHeaderIndexes(),
                    )
                }
            }
        }
    }

    private fun List<Manga>.toUpcomingUIModels(): List<UpcomingUIModel> {
        var mangaCount = 0
        return fastMap { UpcomingUIModel.Item(it) }
            .insertSeparatorsReversed { before, after ->
                if (after != null) mangaCount++

                val beforeDate = before?.manga
                    ?.expectedNextUpdate
                    ?.toLocalDateTime(TimeZone.currentSystemDefault())
                    ?.date
                val afterDate = after?.manga
                    ?.expectedNextUpdate
                    ?.toLocalDateTime(TimeZone.currentSystemDefault())
                    ?.date

                if (beforeDate != afterDate && afterDate != null) {
                    UpcomingUIModel.Header(afterDate, mangaCount).also { mangaCount = 0 }
                } else {
                    null
                }
            }
    }

    private fun List<UpcomingUIModel>.toEvents(): Map<LocalDate, Int> {
        return filterIsInstance<UpcomingUIModel.Header>()
            .associate { it.date to it.mangaCount }
    }

    private fun List<UpcomingUIModel>.getHeaderIndexes(): Map<LocalDate, Int> {
        return fastMapIndexedNotNull { index, upcomingUIModel ->
            if (upcomingUIModel is UpcomingUIModel.Header) {
                upcomingUIModel.date to index
            } else {
                null
            }
        }
            .toMap()
    }

    fun setSelectedYearMonth(yearMonth: YearMonth) {
        mutableState.update { it.copy(selectedYearMonth = yearMonth) }
    }

    data class State(
        val selectedYearMonth: YearMonth = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
            .yearMonth,
        val items: List<UpcomingUIModel> = listOf(),
        val events: Map<LocalDate, Int> = mapOf(),
        val headerIndexes: Map<LocalDate, Int> = mapOf(),
    )
}
