package mihon.desktop.library.update

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mihon.desktop.preferences.DesktopPreferenceStore

class LibraryUpdateScheduler(
    private val updateService: LibraryUpdateService,
    private val preferenceStore: DesktopPreferenceStore,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _isUpdating = MutableStateFlow(false)
    val isUpdating: StateFlow<Boolean> = _isUpdating.asStateFlow()

    private val _currentProgress = MutableStateFlow<LibraryUpdateProgress?>(null)
    val currentProgress: StateFlow<LibraryUpdateProgress?> = _currentProgress.asStateFlow()

    private val _lastReport = MutableStateFlow<LibraryUpdateReport?>(null)
    val lastReport: StateFlow<LibraryUpdateReport?> = _lastReport.asStateFlow()

    private val updateMutex = Mutex()
    private var periodicJob: Job? = null

    init {
        startPeriodicCheck()
    }

    fun startPeriodicCheck(checkIntervalMs: Long = 60_000L) {
        periodicJob?.cancel()
        periodicJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(checkIntervalMs)
                try {
                    checkAndRunAutoUpdate()
                } catch (_: Exception) {
                    // Ignore scheduled update errors
                }
            }
        }
    }

    suspend fun checkAndRunAutoUpdate(): LibraryUpdateReport? {
        val prefs = preferenceStore.load()
        val intervalHours = prefs.libraryUpdateIntervalHours
        if (intervalHours <= 0) return null

        val now = clock()
        val intervalMillis = intervalHours.toLong() * 3_600_000L
        val lastUpdate = prefs.lastLibraryUpdateEpochMillis
        if (now - lastUpdate < intervalMillis) {
            return null
        }

        return runUpdateInternal(
            options = LibraryUpdateOptions(
                skipCompleted = prefs.libraryUpdateSkipCompleted,
                skipUnread = prefs.libraryUpdateSkipUnread,
                skipNotStarted = prefs.libraryUpdateSkipStarted,
                includedCategoryIds = prefs.libraryUpdateCategories.takeIf(Set<Long>::isNotEmpty),
                excludedCategoryIds = prefs.libraryUpdateCategoriesExclude.takeIf(Set<Long>::isNotEmpty),
                autoDownloadNewChapters = prefs.autoDownloadNewChapters,
            ),
        )
    }

    suspend fun triggerUpdateNow(): LibraryUpdateReport? {
        val prefs = preferenceStore.load()
        return runUpdateInternal(
            options = LibraryUpdateOptions(
                skipCompleted = prefs.libraryUpdateSkipCompleted,
                skipUnread = prefs.libraryUpdateSkipUnread,
                skipNotStarted = prefs.libraryUpdateSkipStarted,
                includedCategoryIds = prefs.libraryUpdateCategories.takeIf(Set<Long>::isNotEmpty),
                excludedCategoryIds = prefs.libraryUpdateCategoriesExclude.takeIf(Set<Long>::isNotEmpty),
                autoDownloadNewChapters = prefs.autoDownloadNewChapters,
            ),
        )
    }

    private suspend fun runUpdateInternal(options: LibraryUpdateOptions): LibraryUpdateReport? {
        if (!updateMutex.tryLock()) {
            return null // Already updating
        }

        try {
            _isUpdating.value = true
            _currentProgress.value = null

            val report = updateService.updateLibrary(
                options = options,
                onProgress = { progress -> _currentProgress.value = progress },
            )

            _lastReport.value = report
            val now = clock()
            val currentPrefs = preferenceStore.load()
            preferenceStore.save(currentPrefs.copy(lastLibraryUpdateEpochMillis = now))

            return report
        } finally {
            _currentProgress.value = null
            _isUpdating.value = false
            updateMutex.unlock()
        }
    }
}
