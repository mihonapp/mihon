package mihon.feature.upcoming

import androidx.compose.runtime.Immutable
import androidx.compose.ui.util.fastMap
import androidx.compose.ui.util.fastMapIndexedNotNull
import androidx.lifecycle.viewModelScope
import eu.kanade.core.util.insertSeparatorsReversed
import eu.kanade.tachiyomi.util.lang.toLocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import mihon.core.viewmodel.StateViewModel
import mihon.domain.upcoming.interactor.GetUpcomingManga
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.upcoming.service.UpcomingPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.LocalDate
import java.time.YearMonth

class UpcomingViewModel(
    private val getUpcomingManga: GetUpcomingManga = Injekt.get(),
    val getCategories: GetCategories = Injekt.get(),
    val upcomingPreferences: UpcomingPreferences = Injekt.get(),
) : StateViewModel<UpcomingViewModel.State>(State()) {

    val excludedCategories = upcomingPreferences.filterExcludedCategories
    val includedCategories = upcomingPreferences.filterIncludedCategories

    init {
        viewModelScope.launchIO {
            getUpcomingItemPreferenceFlow()
                .distinctUntilChanged()
                .flatMapLatest {
                    getUpcomingManga.subscribe(
                        excludedCategories = it.filterExcludedCategories,
                        includedCategories = it.filterIncludedCategories,
                    )
                        .distinctUntilChanged()
                }
                .collectLatest {
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

                val beforeDate = before?.manga?.expectedNextUpdate?.toLocalDate()
                val afterDate = after?.manga?.expectedNextUpdate?.toLocalDate()

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

    private fun getUpcomingItemPreferenceFlow(): Flow<ItemPreferences> {
        return combine(
            upcomingPreferences.filterExcludedCategories.changes(),
            upcomingPreferences.filterIncludedCategories.changes(),
        ) { excluded, included ->
            ItemPreferences(
                filterExcludedCategories = excluded,
                filterIncludedCategories = included,
            )
        }
    }

    fun resetDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    fun showFilterDialog() {
        mutableState.update { it.copy(dialog = Dialog.FilterSheet) }
    }

    fun cycleCategory(category: Category) {
        when (category.id) {
            in includedCategories.get() -> {
                includedCategories.getAndSet { it - category.id }
                excludedCategories.getAndSet { it + category.id }
            }

            in excludedCategories.get() -> excludedCategories.getAndSet { it - category.id }
            else -> includedCategories.getAndSet { it + category.id }
        }
    }

    @Immutable
    private data class ItemPreferences(
        val filterExcludedCategories: List<Long>,
        val filterIncludedCategories: List<Long>,
    )

    data class State(
        val selectedYearMonth: YearMonth = YearMonth.now(),
        val items: List<UpcomingUIModel> = listOf(),
        val events: Map<LocalDate, Int> = mapOf(),
        val headerIndexes: Map<LocalDate, Int> = mapOf(),
        val hasActiveFilters: Boolean = false,
        val dialog: Dialog? = null,
    )

    sealed interface Dialog {
        data object FilterSheet : Dialog
    }
}
