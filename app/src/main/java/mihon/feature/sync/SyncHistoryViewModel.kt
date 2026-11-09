package mihon.feature.sync

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.sync.SyncDevice
import mihon.sync.SyncHistory
import mihon.sync.model.SyncDeviceInfo
import mihon.sync.model.SyncHistoryEntry
import tachiyomi.core.common.util.system.logcat

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class SyncHistoryViewModel(
    private val syncHistory: SyncHistory,
    private val syncDevice: SyncDevice,
) : ViewModel() {

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    val thisDeviceId: String get() = syncDevice.id

    init {
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(loading = true, error = false) }

        viewModelScope.launch {
            try {
                val entries = syncHistory.read()
                val devices = syncHistory.readDevices()
                _state.update { State(loading = false, entries = entries, devices = devices) }
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Could not load the sync history" }
                _state.update { State(loading = false, error = true) }
            }
        }
    }

    /**
     * Empties the shared log — for every device — then shows what is left, which is nothing.
     */
    fun clear(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val cleared = syncHistory.clear()
            onResult(cleared)
            refresh()
        }
    }

    @Immutable
    data class State(
        val loading: Boolean = true,
        val error: Boolean = false,
        val entries: List<SyncHistoryEntry> = emptyList(),
        val devices: List<SyncDeviceInfo> = emptyList(),
    )
}
