package eu.kanade.tachiyomi.ui.reader.cast

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.util.system.notificationBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import logcat.LogPriority
import mihon.app.di.appGraph
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR

/**
 * Foreground service that keeps the process alive while casting and exposes previous / next /
 * stop controls in an ongoing notification. It follows [CastController.state] and stops itself
 * once casting ends.
 */
class CastService : Service() {

    private val controller: CastController by lazy { applicationContext.appGraph.castController }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var observing = false
    private var postedContent: NotificationContent? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Always promote to foreground first: the system requires it right after startForegroundService.
        if (!postToForeground(currentContent())) {
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent?.action) {
            ACTION_NEXT -> controller.remoteNextPage()
            ACTION_PREVIOUS -> controller.remotePreviousPage()
            ACTION_STOP -> {
                controller.stopCasting(null)
                finish()
                return START_NOT_STICKY
            }
        }

        if (!observing) {
            observing = true
            combine(controller.state, controller.webInfo) { state, webInfo -> NotificationContent(state, webInfo) }
                .distinctUntilChanged()
                .onEach { content ->
                    if (content.active) {
                        if (!postToForeground(content)) finish()
                    } else {
                        finish()
                    }
                }
                .launchIn(scope)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun finish() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun currentContent(): NotificationContent {
        return NotificationContent(controller.state.value, controller.webInfo.value)
    }

    /** Posts (or updates) the foreground notification; false if the system refused it. */
    private fun postToForeground(content: NotificationContent): Boolean {
        if (content == postedContent) return true
        return try {
            ServiceCompat.startForeground(
                this,
                Notifications.ID_CAST,
                buildNotification(content),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                } else {
                    0
                },
            )
            postedContent = content
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Unable to start the cast foreground service" }
            false
        }
    }

    private fun buildNotification(content: NotificationContent): Notification {
        val target = when (content.targetType) {
            CastTargetType.WEB -> stringResource(MR.strings.cast_notification_text_web, content.webUrl)
            else -> stringResource(MR.strings.cast_notification_text_display)
        }
        val text = listOf(content.chapterName, target)
            .filter { it.isNotBlank() }
            .joinToString(" · ")

        return notificationBuilder(Notifications.CHANNEL_CAST) {
            setSmallIcon(R.drawable.ic_cast_24dp)
            setContentTitle(stringResource(MR.strings.cast_notification_title, content.mangaTitle))
            setContentText(text)
            setOngoing(true)
            setOnlyAlertOnce(true)
            setSilent(true)
            setShowWhen(false)
            setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            if (content.mangaId > 0 && content.chapterId > 0) {
                setContentIntent(readerPendingIntent(content.mangaId, content.chapterId))
            }
            addAction(
                R.drawable.ic_skip_previous_24dp,
                stringResource(MR.strings.cast_remote_prev_page),
                servicePendingIntent(ACTION_PREVIOUS, REQUEST_PREVIOUS),
            )
            addAction(
                R.drawable.ic_skip_next_24dp,
                stringResource(MR.strings.cast_remote_next_page),
                servicePendingIntent(ACTION_NEXT, REQUEST_NEXT),
            )
            addAction(
                R.drawable.ic_stop_24dp,
                stringResource(MR.strings.cast_action_stop),
                servicePendingIntent(ACTION_STOP, REQUEST_STOP),
            )
        }.build()
    }

    private fun readerPendingIntent(mangaId: Long, chapterId: Long): PendingIntent {
        return PendingIntent.getActivity(
            this,
            REQUEST_OPEN_READER,
            ReaderActivity.newIntent(this, mangaId, chapterId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent {
        return PendingIntent.getService(
            this,
            requestCode,
            Intent(this, CastService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** The subset of the cast state the notification depends on, so position updates don't re-post it. */
    private data class NotificationContent(
        val active: Boolean,
        val targetType: CastTargetType?,
        val webUrl: String,
        val mangaId: Long,
        val mangaTitle: String,
        val chapterId: Long,
        val chapterName: String,
    ) {
        constructor(state: CastState, webInfo: CastWebInfo?) : this(
            active = state.active,
            targetType = state.targetType,
            webUrl = webInfo?.url ?: state.targetName.orEmpty(),
            mangaId = state.mangaId,
            mangaTitle = state.mangaTitle,
            chapterId = state.chapterId,
            chapterName = state.chapterName,
        )
    }

    companion object {
        const val ACTION_NEXT = "eu.kanade.tachiyomi.CAST_NEXT"
        const val ACTION_PREVIOUS = "eu.kanade.tachiyomi.CAST_PREVIOUS"
        const val ACTION_STOP = "eu.kanade.tachiyomi.CAST_STOP"

        private const val REQUEST_OPEN_READER = 0
        private const val REQUEST_PREVIOUS = 1
        private const val REQUEST_NEXT = 2
        private const val REQUEST_STOP = 3

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, CastService::class.java))
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Unable to start the cast service" }
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CastService::class.java))
        }
    }
}
