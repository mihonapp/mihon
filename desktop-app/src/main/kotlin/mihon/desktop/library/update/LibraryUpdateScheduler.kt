package mihon.desktop.library.update

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.desktop.preferences.DesktopPreferences
import java.nio.file.Path
import kotlin.coroutines.coroutineContext

class LibraryUpdateScheduler(
    private val updateService: LibraryUpdateService,
    private val preferenceStore: DesktopPreferenceStore,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    recoveryFile: Path? = null,
    startAutomatically: Boolean = true,
) {
    private val _isUpdating = MutableStateFlow(false)
    val isUpdating: StateFlow<Boolean> = _isUpdating.asStateFlow()
    private val _currentProgress = MutableStateFlow<LibraryUpdateProgress?>(null)
    val currentProgress: StateFlow<LibraryUpdateProgress?> = _currentProgress.asStateFlow()
    private val _lastReport = MutableStateFlow<LibraryUpdateReport?>(null)
    val lastReport: StateFlow<LibraryUpdateReport?> = _lastReport.asStateFlow()
    private val stateStore = LibraryUpdateStateStore(recoveryFile)
    private val _runState = MutableStateFlow(stateStore.load())
    val runState: StateFlow<LibraryUpdateRunState> = _runState.asStateFlow()
    private val updateMutex = Mutex()
    private var periodicJob: Job? = null

    @Volatile private var activeUpdateJob: Job? = null

    init {
        if (startAutomatically) startPeriodicCheck()
    }

    fun startPeriodicCheck(checkIntervalMs: Long = 60_000L) {
        require(checkIntervalMs > 0)
        periodicJob?.cancel()
        periodicJob = scope.launch(Dispatchers.Default) {
            // One bounded catch-up, never one job per missed interval.
            while (isActive) {
                try {
                    checkAndRunAutoUpdate()
                } catch (error: CancellationException) {
                    if (!isActive) throw error
                } catch (error: Exception) {
                    _runState.value = _runState.value.copy(status = LibraryUpdateStatus.FAILED, error = error.message)
                }
                delay(checkIntervalMs)
            }
        }
    }

    fun cancelUpdate() {
        activeUpdateJob?.cancel(CancellationException("Library update cancelled"))
    }

    fun stop() {
        periodicJob?.cancel()
        cancelUpdate()
    }

    suspend fun checkAndRunAutoUpdate(): LibraryUpdateReport? = coroutineScope { runUpdateInternal(automatic = true) }

    suspend fun triggerUpdateNow(): LibraryUpdateReport? = coroutineScope { runUpdateInternal(automatic = false) }

    private suspend fun runUpdateInternal(automatic: Boolean): LibraryUpdateReport? {
        if (!updateMutex.tryLock()) return null
        try {
            val prefs = preferenceStore.load()
            val previous = _runState.value
            val now = clock()
            val intervalMillis = prefs.libraryUpdateIntervalHours.toLong() * 3_600_000L
            val fullUpdateDue = now - prefs.lastLibraryUpdateEpochMillis >= intervalMillis
            if (automatic) {
                if (prefs.libraryUpdateIntervalHours <= 0) return null
                if (previous.retryAfterEpochMillis > now && (!fullUpdateDue || previous.failedMangaIds.isEmpty())) {
                    return null
                }
                val retryPending = previous.status in setOf(
                    LibraryUpdateStatus.RUNNING,
                    LibraryUpdateStatus.FAILED,
                    LibraryUpdateStatus.CANCELLED,
                )
                if (!retryPending && !fullUpdateDue) return null
            }
            val retryIds = previous.failedMangaIds.takeIf { automatic && !fullUpdateDue && it.isNotEmpty() }
            activeUpdateJob = coroutineContext[Job]
            _isUpdating.value = true
            _currentProgress.value = null
            persist(previous.copy(status = LibraryUpdateStatus.RUNNING, lastAttemptEpochMillis = now))
            try {
                val report = updateService.updateLibrary(
                    options = prefs.options().copy(mangaIds = retryIds),
                    onProgress = { _currentProgress.value = it },
                )
                _lastReport.value = report
                val completedAt = clock()
                val failed = report.results.filter { it.error != null }.map { it.mangaId }.toSet()
                val next = if (failed.isEmpty()) {
                    LibraryUpdateRunState(
                        status = LibraryUpdateStatus.COMPLETED,
                        lastAttemptEpochMillis = now,
                        lastCompletedEpochMillis = completedAt,
                    )
                } else {
                    retryState(previous, LibraryUpdateStatus.FAILED, report.errors.joinToString("\n")).copy(
                        lastAttemptEpochMillis = now,
                        lastCompletedEpochMillis = completedAt,
                        failedMangaIds = failed,
                    )
                }
                persist(next)
                if (retryIds == null) {
                    preferenceStore.save(preferenceStore.load().copy(lastLibraryUpdateEpochMillis = completedAt))
                }
                return report
            } catch (error: CancellationException) {
                try {
                    persist(retryState(_runState.value, LibraryUpdateStatus.CANCELLED, error.message))
                } catch (persistenceError: Exception) {
                    error.addSuppressed(persistenceError)
                }
                throw error
            } catch (error: Exception) {
                persist(retryState(_runState.value, LibraryUpdateStatus.FAILED, error.message))
                throw error
            }
        } finally {
            activeUpdateJob = null
            _currentProgress.value = null
            _isUpdating.value = false
            updateMutex.unlock()
        }
    }

    private fun retryState(
        previous: LibraryUpdateRunState,
        status: LibraryUpdateStatus,
        error: String?,
    ) = previous.copy(
        status = status,
        retryCount = (previous.retryCount + 1).coerceAtMost(16),
        retryAfterEpochMillis = clock() + retryDelay(previous.retryCount),
        error = error,
    )

    private fun retryDelay(count: Int) = (300_000L * (1L shl count.coerceIn(0, 7))).coerceAtMost(21_600_000L)

    private fun persist(state: LibraryUpdateRunState) {
        _runState.value = state
        stateStore.save(state)
    }

    private fun DesktopPreferences.options() = LibraryUpdateOptions(
        skipCompleted = libraryUpdateSkipCompleted,
        skipUnread = libraryUpdateSkipUnread,
        skipNotStarted = libraryUpdateSkipStarted,
        includedCategoryIds = libraryUpdateCategories.takeIf(Set<Long>::isNotEmpty),
        excludedCategoryIds = libraryUpdateCategoriesExclude.takeIf(Set<Long>::isNotEmpty),
        autoDownloadNewChapters = autoDownloadNewChapters,
    )
}
