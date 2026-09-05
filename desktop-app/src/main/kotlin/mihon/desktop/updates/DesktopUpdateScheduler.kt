package mihon.desktop.updates

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.hours

class DesktopUpdateScheduler(
    private val updateService: DesktopLibraryUpdateService,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    var intervalHours: Long = 12L, // 0 = manual only
) {
    private var schedulerJob: Job? = null
    private val _isUpdating = MutableStateFlow(false)
    val isUpdating: StateFlow<Boolean> = _isUpdating.asStateFlow()

    private val _lastResult = MutableStateFlow<LibraryUpdateResult?>(null)
    val lastResult: StateFlow<LibraryUpdateResult?> = _lastResult.asStateFlow()

    fun start() {
        if (intervalHours <= 0) return
        if (schedulerJob?.isActive == true) return

        schedulerJob = scope.launch {
            while (true) {
                delay(intervalHours.hours)
                triggerNow()
            }
        }
    }

    fun stop() {
        schedulerJob?.cancel()
        schedulerJob = null
    }

    suspend fun triggerNow(): LibraryUpdateResult {
        if (_isUpdating.value) {
            return _lastResult.value ?: LibraryUpdateResult(0, 0, 0, emptyList())
        }
        _isUpdating.value = true
        return try {
            val result = updateService.updateLibrary()
            _lastResult.value = result
            result
        } finally {
            _isUpdating.value = false
        }
    }
}
