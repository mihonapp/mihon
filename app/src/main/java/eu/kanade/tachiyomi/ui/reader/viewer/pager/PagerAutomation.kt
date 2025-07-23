package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.content.Context
import android.os.PowerManager
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import tachiyomi.core.common.i18n.pluralStringResource
import tachiyomi.i18n.MR

fun automatePager(
    activity: ReaderActivity,
    adapter: PagerViewerAdapter,
    automationInProgress: MutableStateFlow<Boolean>,
    config: PagerConfig,
    scope: CoroutineScope,
    moveToNext: () -> Unit,
) {
    scope.launch {
        automationInProgress.collect { isAutomating ->
            if (isAutomating) {
                activity.hideMenu()
                val powerManager = activity.getSystemService(Context.POWER_SERVICE) as PowerManager
                val lifeCycle = ProcessLifecycleOwner.get().lifecycle
                val startTime = System.currentTimeMillis()
                var lastChapter = adapter.currentChapter?.chapter?.id ?: 0L
                var chaptersAutomated = 0
                val maxMilliseconds = 1000 * 60 * config.automationMaxMinutes
                while (automationInProgress.value) {
                    if (!lifeCycle.currentState.isAtLeast(Lifecycle.State.STARTED) || !powerManager.isInteractive) {
                        Log.d("Automation", "Autostop automation in background")
                        automationInProgress.value = false
                        break
                    }
                    if (maxMilliseconds > 0) {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - startTime > maxMilliseconds) {
                            activity.toast(
                                activity.pluralStringResource(
                                    MR.plurals.reader_automation_max_minutes_reached,
                                    config.automationMaxMinutes,
                                    config.automationMaxMinutes,
                                ),
                            )
                            automationInProgress.value = false
                            break
                        }
                    }
                    val currentChapter = adapter.currentChapter?.chapter?.id ?: 0L
                    if (config.automationMaxChapters > 0) {
                        if (currentChapter != lastChapter) {
                            lastChapter = currentChapter
                            chaptersAutomated++
                            if (chaptersAutomated >= config.automationMaxChapters) {
                                activity.toast(
                                    activity.pluralStringResource(
                                        MR.plurals.reader_automation_max_chapters_reached,
                                        config.automationMaxChapters,
                                        config.automationMaxChapters,
                                    ),
                                )
                                automationInProgress.value = false
                                break
                            }
                        }
                    }
                    android.util.Log.d("Automation", "waiting for ${config.autoFlipInterval}s to flip")
                    delay(config.autoFlipInterval * 1000L)
                    android.util.Log.d("Automation", "flip")
                    moveToNext()
                }
            }
        }
    }
}
