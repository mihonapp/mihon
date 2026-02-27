package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import android.content.Context
import android.os.PowerManager
import android.util.Log
import android.view.Choreographer
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import tachiyomi.core.common.i18n.pluralStringResource
import tachiyomi.i18n.MR
import kotlin.coroutines.resume

fun automateWebtoon(
    activity: ReaderActivity,
    recycler: WebtoonRecyclerView,
    adapter: WebtoonAdapter,
    automationInProgress: MutableStateFlow<Boolean>,
    config: WebtoonConfig,
    scope: CoroutineScope,
) {
    scope.launch {
        automationInProgress.collect { isAutomating ->
            if (isAutomating) {
                activity.hideMenu()
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                var lastRefreshRate = getRefreshRate(activity)
                var scrollDistancePerFrame = getScrollDistancePerFrame(activity, config, lastRefreshRate)
                val powerManager = activity.getSystemService(Context.POWER_SERVICE) as PowerManager
                val lifeCycle = ProcessLifecycleOwner.get().lifecycle
                val startTime = System.currentTimeMillis()
                var lastChapter = adapter.currentChapter?.chapter?.id ?: 0L
                var chaptersAutomated = 0
                val maxMilliseconds = 1000 * 60 * config.automationMaxMinutes
                Log.d("Automation", "Detected refresh rate: ${lastRefreshRate}Hz")
                Log.d("Automation", "Started @ $scrollDistancePerFrame px/frame")
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
                    suspendCancellableCoroutine { continuation ->
                        val frameCallback = Choreographer.FrameCallback {
                            if (continuation.isActive) {
                                continuation.resume(Unit)
                            }
                        }
                        Choreographer.getInstance().postFrameCallback(frameCallback)
                        continuation.invokeOnCancellation {
                            Choreographer.getInstance().removeFrameCallback(frameCallback)
                            Log.d("Automation", "Choreographer callback cancelled", it)
                        }
                    }
                    val newRefreshRate = getRefreshRate(activity)
                    if (newRefreshRate != lastRefreshRate) {
                        lastRefreshRate = newRefreshRate
                        scrollDistancePerFrame = getScrollDistancePerFrame(activity, config, lastRefreshRate)
                        Log.d("Automation", "Refresh rate changed to ${lastRefreshRate}Hz")
                        Log.d("Automation", "Updated @ $scrollDistancePerFrame px/frame")
                    }
                    recycler.scrollBy(0, scrollDistancePerFrame)
                }
            } else {
                Log.d("Automation", "Stopped")
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }
}

private fun getRefreshRate(activity: ReaderActivity): Float {
    return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
        activity.display?.refreshRate ?: 60f
    } else {
        @Suppress("DEPRECATION")
        activity.windowManager.defaultDisplay.refreshRate
    }
}

private fun getScrollDistancePerFrame(activity: ReaderActivity, config: WebtoonConfig, refreshRate: Float): Int {
    val screenHeight = activity.resources.displayMetrics.heightPixels
    val scrollDistancePerFrame = screenHeight / (config.autoScrollSpeed * refreshRate)
    return scrollDistancePerFrame.toInt()
}
