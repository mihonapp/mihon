package eu.kanade.tachiyomi.util.system

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager

val Context.workManager: WorkManager
    get() = WorkManager.getInstance(this)

fun WorkManager.isRunning(tag: String): Boolean {
    val list = this.getWorkInfosByTag(tag).get()
    return list.any { it.state == WorkInfo.State.RUNNING }
}
