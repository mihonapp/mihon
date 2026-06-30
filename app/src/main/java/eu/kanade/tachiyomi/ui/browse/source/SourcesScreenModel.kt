package eu.kanade.tachiyomi.ui.browse.source

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.source.interactor.DeleteSourcePinGroup
import eu.kanade.domain.source.interactor.GetEnabledSources
import eu.kanade.domain.source.interactor.GetSourcePinGroups
import eu.kanade.domain.source.interactor.SetSourcePinGroups
import eu.kanade.domain.source.interactor.ToggleSource
import eu.kanade.domain.source.interactor.ToggleSourcePin
import eu.kanade.presentation.browse.SourceUiModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.source.model.Pin
import tachiyomi.domain.source.model.Source
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.TreeMap

class SourcesScreenModel(
    private val getEnabledSources: GetEnabledSources = Injekt.get(),
    private val toggleSource: ToggleSource = Injekt.get(),
    private val toggleSourcePin: ToggleSourcePin = Injekt.get(),
    private val setSourcePinGroups: SetSourcePinGroups = Injekt.get(),
    private val getSourcePinGroups: GetSourcePinGroups = Injekt.get(),
    private val deleteSourcePinGroup: DeleteSourcePinGroup = Injekt.get(),
) : StateScreenModel<SourcesScreenModel.State>(State()) {

    private val _events = Channel<Event>(Int.MAX_VALUE)
    val events = _events.receiveAsFlow()

    init {
        screenModelScope.launchIO {
            getEnabledSources.subscribe()
                .catch {
                    logcat(LogPriority.ERROR, it)
                    _events.send(Event.FailedFetchingSources)
                }
                .collectLatest(::collectLatestSources)
        }
    }

    private fun collectLatestSources(sources: List<Source>) {
        mutableState.update { state ->
            val map = TreeMap<String, MutableList<Source>> { d1, d2 ->
                // Sources without a lang defined will be placed at the end
                when {
                    d1 == LAST_USED_KEY && d2 != LAST_USED_KEY -> -1
                    d2 == LAST_USED_KEY && d1 != LAST_USED_KEY -> 1
                    d1 == PINNED_KEY && d2 != PINNED_KEY -> -1
                    d2 == PINNED_KEY && d1 != PINNED_KEY -> 1
                    d1 == "" && d2 != "" -> 1
                    d2 == "" && d1 != "" -> -1
                    else -> d1.compareTo(d2)
                }
            }
            val byLang = sources.groupByTo(map) {
                when {
                    it.isUsedLast -> LAST_USED_KEY
                    Pin.Actual in it.pin -> PINNED_KEY
                    else -> it.lang
                }
            }

            val groupNames = sources.flatMap { it.pinnedGroups }.distinct().sorted()
            val groupSections = groupNames.flatMap { group ->
                listOf<SourceUiModel>(SourceUiModel.Header(group, isGroup = true)) +
                    sources.filter { !it.isUsedLast && group in it.pinnedGroups }
                        .map { SourceUiModel.Item(it, sectionKey = group) }
            }

            val items = buildList {
                var groupsInserted = false
                for ((key, value) in byLang) {
                    val isSpecial = key == LAST_USED_KEY || key == PINNED_KEY
                    if (!isSpecial && !groupsInserted) {
                        addAll(groupSections)
                        groupsInserted = true
                    }
                    add(SourceUiModel.Header(key))
                    value.forEach { add(SourceUiModel.Item(it)) }
                }
                if (!groupsInserted) addAll(groupSections)
            }

            state.copy(
                isLoading = false,
                items = items,
            )
        }
    }

    fun toggleSource(source: Source) {
        toggleSource.await(source)
    }

    fun togglePin(source: Source) {
        toggleSourcePin.await(source)
    }

    fun setSourcePinGroups(source: Source, pinGroups: Set<String>) {
        setSourcePinGroups.await(source, pinGroups)
    }

    fun removeSourceFromGroup(source: Source, group: String) {
        setSourcePinGroups.await(source, source.pinnedGroups - group)
    }

    fun getSourcePinGroups(source: Source): Pair<List<String>, List<Boolean>> {
        return getSourcePinGroups.execute(source)
    }

    fun deleteSourcePinGroup(pinGroup: String) {
        deleteSourcePinGroup.await(pinGroup)
    }

    fun showSourceDialog(source: Source) {
        mutableState.update { it.copy(dialog = Dialog.SourceOptions(source)) }
    }

    fun showPinGroupsDialog(source: Source) {
        mutableState.update { it.copy(dialog = Dialog.PinGroups(source)) }
    }

    fun closeDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    sealed interface Event {
        data object FailedFetchingSources : Event
    }

    sealed interface Dialog {
        data class SourceOptions(val source: Source) : Dialog
        data class PinGroups(val source: Source) : Dialog
    }

    @Immutable
    data class State(
        val dialog: Dialog? = null,
        val isLoading: Boolean = true,
        val items: List<SourceUiModel> = listOf(),
    ) {
        val isEmpty = items.isEmpty()
    }

    companion object {
        const val PINNED_KEY = "pinned"
        const val LAST_USED_KEY = "last_used"
    }
}
