package eu.kanade.presentation.more.settings.screen.debug

import androidx.work.WorkManager
import kotlinx.coroutines.guava.await
import java.util.UUID

internal suspend fun cancelActiveWorker(workManager: WorkManager, id: UUID): Boolean {
    val workInfo = workManager.getWorkInfoById(id).await()
    if (workInfo == null || workInfo.state.isFinished) return false

    workManager.cancelWorkById(id).result.await()
    return true
}
