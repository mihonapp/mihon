package eu.kanade.tachiyomi.ui.more

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.result.ResultEffect
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.core.preference.asState
import eu.kanade.domain.base.BasePreferences
import eu.kanade.presentation.more.MoreScreen
import eu.kanade.presentation.util.isTabletUi
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.ui.home.TabReselectEventKey
import eu.kanade.tachiyomi.ui.setting.addSettingsRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import mihon.core.navigation.CategoryRoute
import mihon.core.navigation.DownloadQueueRoute
import mihon.core.navigation.SettingsRoute
import mihon.core.navigation.StatsRoute
import mihon.core.navigation.SupportUsRoute
import mihon.core.navigation.domain.SettingsDestination
import mihon.navigation.util.LocalBackStack
import tachiyomi.core.common.util.lang.launchIO

@Composable
fun MoreTab() {
    val backStack = LocalBackStack.current
    val isTabletUi = isTabletUi()
    val viewModel = metroViewModel<MoreViewModel>()
    val downloadQueueState by viewModel.downloadQueueState.collectAsState()
    MoreScreen(
        downloadQueueStateProvider = { downloadQueueState },
        downloadedOnly = viewModel.downloadedOnly,
        onDownloadedOnlyChange = { viewModel.downloadedOnly = it },
        incognitoMode = viewModel.incognitoMode,
        onIncognitoModeChange = { viewModel.incognitoMode = it },
        onClickDownloadQueue = { backStack.add(DownloadQueueRoute) },
        onClickCategories = { backStack.add(CategoryRoute) },
        onClickStats = { backStack.add(StatsRoute) },
        onClickDataAndStorage = {
            backStack.addSettingsRoute(SettingsRoute(SettingsDestination.DataAndStorage), isTabletUi)
        },
        onClickSettings = { backStack.addSettingsRoute(SettingsRoute(), isTabletUi) },
        onClickSupport = { backStack.add(SupportUsRoute) },
        onClickAbout = { backStack.addSettingsRoute(SettingsRoute(SettingsDestination.About), isTabletUi) },
    )

    ResultEffect<Unit>(resultKey = TabReselectEventKey) {
        backStack.addSettingsRoute(SettingsRoute(), isTabletUi)
    }
}

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class MoreViewModel(
    private val downloadManager: DownloadManager,
    preferences: BasePreferences,
) : ViewModel() {

    var downloadedOnly by preferences.downloadedOnly.asState(viewModelScope)
    var incognitoMode by preferences.incognitoMode.asState(viewModelScope)

    private var _downloadQueueState: MutableStateFlow<DownloadQueueState> = MutableStateFlow(DownloadQueueState.Stopped)
    val downloadQueueState: StateFlow<DownloadQueueState> = _downloadQueueState.asStateFlow()

    init {
        // Handle running/paused status change and queue progress updating
        viewModelScope.launchIO {
            combine(
                downloadManager.isDownloaderRunning,
                downloadManager.queueState,
            ) { isRunning, downloadQueue -> Pair(isRunning, downloadQueue.size) }
                .collectLatest { (isDownloading, downloadQueueSize) ->
                    val pendingDownloadExists = downloadQueueSize != 0
                    _downloadQueueState.value = when {
                        !pendingDownloadExists -> DownloadQueueState.Stopped
                        !isDownloading -> DownloadQueueState.Paused(downloadQueueSize)
                        else -> DownloadQueueState.Downloading(downloadQueueSize)
                    }
                }
        }
    }
}

sealed interface DownloadQueueState {
    data object Stopped : DownloadQueueState
    data class Paused(val pending: Int) : DownloadQueueState
    data class Downloading(val pending: Int) : DownloadQueueState
}
