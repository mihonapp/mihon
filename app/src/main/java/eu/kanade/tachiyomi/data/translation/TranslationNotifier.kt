package eu.kanade.tachiyomi.data.translation

import android.app.Application
import android.app.Notification
import androidx.core.app.NotificationCompat
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.data.notification.NotificationHandler
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.notify
import tachiyomi.data.Translation_jobs
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class TranslationNotifier(
    private val context: Application = Injekt.get(),
    private val repository: TranslationRepository = Injekt.get(),
    private val securityPreferences: SecurityPreferences = Injekt.get(),
) {
    fun foregroundNotification(): Notification {
        return context.notificationBuilder(Notifications.CHANNEL_TRANSLATION_PROGRESS) {
            setContentTitle("Translation queue")
            setContentText("Preparing")
            setSmallIcon(android.R.drawable.stat_sys_upload)
            setOngoing(true)
            setOnlyAlertOnce(true)
            setContentIntent(NotificationHandler.openTranslationQueuePendingActivity(context))
            setProgress(0, 0, true)
        }.build()
    }

    suspend fun showJobProgress(
        job: Translation_jobs,
        current: Long,
        total: Long,
        status: TranslationJobStatus,
        message: String? = null,
    ) {
        val item = repository.getJobForQueue(job._id)
        val state = TranslationNotificationFormatter.format(
            item = item,
            job = job,
            current = current,
            total = total,
            status = status,
            message = message,
            hideContent = securityPreferences.hideNotificationContent.get(),
        )
        val notification = context.notificationBuilder(Notifications.CHANNEL_TRANSLATION_PROGRESS) {
            setContentTitle(state.title)
            setContentText(state.text)
            setStyle(NotificationCompat.BigTextStyle().bigText(state.bigText))
            setSmallIcon(android.R.drawable.stat_sys_upload)
            setOngoing(status == TranslationJobStatus.Running || status == TranslationJobStatus.Retrying)
            setOnlyAlertOnce(true)
            setContentIntent(NotificationHandler.openTranslationQueuePendingActivity(context))
            setProgress(state.progressMax, state.progressCurrent, state.indeterminate)
        }.build()
        context.notify(Notifications.ID_TRANSLATION_PROGRESS, notification)
    }
}
