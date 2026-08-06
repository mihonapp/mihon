package eu.kanade.tachiyomi.ui.more

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.work.WorkInfo
import eu.kanade.tachiyomi.data.updater.AppUpdateDownloadJob
import eu.kanade.tachiyomi.extension.util.ExtensionInstaller
import eu.kanade.tachiyomi.util.storage.getUriCompat
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import mihon.core.viewmodel.StateViewModel
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NewUpdateScreenModel(
    changelogInfo: String,
    private val downloadLink: String,
    private val context: Context = Injekt.get(),
) : StateViewModel<NewUpdateScreenModel.State>(State(changelogInfo = changelogInfo)) {

    init {
        context.workManager.getWorkInfosByTagFlow(AppUpdateDownloadJob.TAG)
            .mapNotNull { it.firstOrNull() }
            .map { workInfo ->
                val progress = if (workInfo.state.isFinished) {
                    workInfo.outputData.getInt(AppUpdateDownloadJob.PROGRESS, 0)
                } else {
                    workInfo.progress.getInt(AppUpdateDownloadJob.PROGRESS, 0)
                }
                val url = if (workInfo.state.isFinished) {
                    workInfo.outputData.getString(AppUpdateDownloadJob.EXTRA_DOWNLOAD_URL)
                } else {
                    workInfo.progress.getString(AppUpdateDownloadJob.EXTRA_DOWNLOAD_URL)
                }

                if (url != downloadLink) {
                    return@map 0 to Stage.Available
                }

                val stage = when {
                    workInfo.state == WorkInfo.State.FAILED -> Stage.Failed
                    workInfo.state.isFinished && progress == 100 -> {
                        if (AppUpdateDownloadJob.updateApk(context).exists()) {
                            Stage.Downloaded
                        } else {
                            Stage.Available
                        }
                    }
                    workInfo.state in listOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING) -> Stage.Downloading
                    else -> Stage.Available
                }
                progress to stage
            }
            .distinctUntilChanged()
            .onEach { (progress, stage) ->
                mutableState.update {
                    it.copy(
                        downloadProgress = progress,
                        stage = stage,
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun startDownload() {
        mutableState.update { it.copy(downloadProgress = 0, stage = Stage.Downloading) }
        AppUpdateDownloadJob.start(context, downloadLink)
    }

    fun installUpdate() {
        val apkFile = AppUpdateDownloadJob.updateApk(context)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkFile.getUriCompat(context), ExtensionInstaller.APK_MIME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }

    override fun onCleared() {
        AppUpdateDownloadJob.stop(context)
    }

    @Immutable
    data class State(
        val changelogInfo: String,
        val downloadProgress: Int = 0,
        val stage: Stage = Stage.Available,
    )

    enum class Stage {
        Available,
        Downloading,
        Downloaded,
        Failed,
    }

    companion object {
        val CHANGELOG_INFO_KEY = CreationExtras.Key<String>()
        val DOWNLOAD_LINK_KEY = CreationExtras.Key<String>()

        val Factory = viewModelFactory {
            initializer {
                NewUpdateScreenModel(
                    changelogInfo = get(CHANGELOG_INFO_KEY)!!,
                    downloadLink = get(DOWNLOAD_LINK_KEY)!!,
                )
            }
        }
    }
}
