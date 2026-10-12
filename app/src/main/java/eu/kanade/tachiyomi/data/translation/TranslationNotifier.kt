package eu.kanade.tachiyomi.data.translation

import android.app.Notification
import android.content.Context
import android.text.format.Formatter
import androidx.core.app.NotificationCompat
import androidx.work.WorkManager
import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.notify
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import java.util.UUID
import mihon.icons.materialsymbols.R as MaterialSymbolsR

/**
 * Builds and shows every notification of the chapter translation worker.
 * Messages are written for the user and never contain the API key or image data.
 */
@Inject
class TranslationNotifier(
    private val context: Context,
    private val securityPreferences: SecurityPreferences,
) {

    /** Chapter name for notification text, or a generic label when the user hides notification content. */
    fun label(chapterName: String): String {
        return if (securityPreferences.hideNotificationContent.get()) {
            context.stringResource(MR.strings.pref_category_translation)
        } else {
            chapterName
        }
    }

    /** Ongoing progress notification, also used as the foreground notification of the worker. */
    fun progressNotification(workId: UUID, chapterName: String, current: Int, total: Int): Notification {
        val cancelIntent = WorkManager.getInstance(context).createCancelPendingIntent(workId)
        return context.notificationBuilder(Notifications.CHANNEL_TRANSLATION_PROGRESS) {
            setContentTitle(
                context.stringResource(
                    MR.strings.translation_notification_progress_title,
                    label(chapterName),
                    current,
                    total,
                ),
            )
            setSmallIcon(R.drawable.ic_translate_24dp)
            setOngoing(true)
            setOnlyAlertOnce(true)
            setProgress(total, current, total <= 0)
            addAction(
                MaterialSymbolsR.drawable.rounded_close,
                context.stringResource(MR.strings.action_cancel),
                cancelIntent,
            )
        }.build()
    }

    fun showProgress(workId: UUID, chapterName: String, current: Int, total: Int) {
        context.notify(
            Notifications.ID_TRANSLATION_PROGRESS,
            progressNotification(workId, chapterName, current, total),
        )
    }

    fun cancelProgress() {
        context.cancelNotification(Notifications.ID_TRANSLATION_PROGRESS)
    }

    fun showComplete(
        chapterId: Long,
        chapterName: String,
        seriesName: String,
        alreadyTranslated: Boolean,
        coverageWarningPages: Int = 0,
    ) {
        val baseText = context.stringResource(
            if (alreadyTranslated) {
                MR.strings.translation_notification_already_text
            } else {
                MR.strings.translation_notification_complete_text
            },
            label(chapterName),
            seriesName,
        )
        showResult(
            chapterId,
            context.stringResource(MR.strings.translation_notification_complete_title),
            withCoverageWarning(baseText, coverageWarningPages),
        )
    }

    /** "X of Y pages translated", with the reasons why the remaining pages failed. */
    fun showPartial(
        chapterId: Long,
        chapterName: String,
        translated: Int,
        total: Int,
        failures: Collection<TranslationException>,
        coverageWarningPages: Int = 0,
    ) {
        val baseText = context.stringResource(
            MR.strings.translation_notification_partial_text,
            label(chapterName),
            summarizeFailures(failures),
        )
        showResult(
            chapterId,
            context.stringResource(MR.strings.translation_notification_partial_title, translated, total),
            withCoverageWarning(baseText, coverageWarningPages),
        )
    }

    /** A fatal error that stopped the whole job. */
    fun showError(chapterId: Long, chapterName: String, error: TranslationException, translated: Int, total: Int) {
        val title = if (total > 0 && translated > 0) {
            context.stringResource(MR.strings.translation_notification_partial_title, translated, total)
        } else {
            context.stringResource(MR.strings.translation_notification_error_title)
        }
        showResult(chapterId, title, "${label(chapterName)}\n${errorText(error)}")
    }

    fun showUnexpectedError(chapterId: Long, chapterName: String, error: Throwable) {
        val text = context.stringResource(
            MR.strings.translation_error_unexpected,
            error.message ?: error.javaClass.simpleName,
        )
        showResult(
            chapterId,
            context.stringResource(MR.strings.translation_notification_error_title),
            "${label(chapterName)}\n$text",
        )
    }

    private fun showResult(chapterId: Long, title: String, text: String) {
        context.notify(resultId(chapterId), Notifications.CHANNEL_TRANSLATION_RESULT) {
            setContentTitle(title)
            setContentText(text.lineSequence().last())
            setStyle(NotificationCompat.BigTextStyle().bigText(text))
            setSmallIcon(R.drawable.ic_translate_24dp)
            setAutoCancel(true)
        }
    }

    private fun withCoverageWarning(text: String, pages: Int): String {
        if (pages <= 0) return text
        val warning = context.stringResource(MR.strings.translation_notification_coverage_warning, pages)
        return "$text\n$warning"
    }

    private fun resultId(chapterId: Long) = Notifications.ID_TRANSLATION_RESULT_BASE - (chapterId % 10_000).toInt()

    private fun summarizeFailures(failures: Collection<TranslationException>): String {
        val reasons = buildList {
            failures.count { it is TranslationException.ContentBlocked }.takeIf { it > 0 }
                ?.let { add(context.stringResource(MR.strings.translation_reason_blocked, it)) }
            failures.count { it is TranslationException.NoImageReturned }.takeIf { it > 0 }
                ?.let { add(context.stringResource(MR.strings.translation_reason_no_image, it)) }
            failures.count { it is TranslationException.UnreadableAnswer }.takeIf { it > 0 }
                ?.let { add(context.stringResource(MR.strings.translation_reason_unreadable, it)) }
            failures.count { it is TranslationException.OutputTruncated }.takeIf { it > 0 }
                ?.let { add(context.stringResource(MR.strings.translation_reason_output_truncated, it)) }
            failures.count { it is TranslationException.QuotaExceeded }.takeIf { it > 0 }
                ?.let { add(context.stringResource(MR.strings.translation_reason_daily_limit)) }
            failures.count { it is TranslationException.RateLimited }.takeIf { it > 0 }
                ?.let { add(context.stringResource(MR.strings.translation_reason_rate_limited, it)) }
            failures.count { it is TranslationException.ServerError }.takeIf { it > 0 }
                ?.let { add(context.stringResource(MR.strings.translation_reason_server, it)) }
            failures.count { it is TranslationException.NetworkError }.takeIf { it > 0 }
                ?.let { add(context.stringResource(MR.strings.translation_reason_network, it)) }
            failures.count { it is TranslationException.CorruptPage }.takeIf { it > 0 }
                ?.let { add(context.stringResource(MR.strings.translation_reason_corrupt, it)) }
        }
        return reasons.joinToString(", ")
    }

    private fun errorText(error: TranslationException): String = when (error) {
        is TranslationException.InvalidApiKey -> context.stringResource(MR.strings.translation_error_invalid_key)
        is TranslationException.QuotaExceeded -> context.stringResource(MR.strings.translation_error_quota)
        is TranslationException.ModelNotFound -> context.stringResource(MR.strings.translation_error_model)
        is TranslationException.RequestRejected -> context.stringResource(
            MR.strings.translation_error_request,
            error.detail ?: "HTTP ${error.httpCode}",
        )
        is TranslationException.Offline -> context.stringResource(MR.strings.translation_error_offline)
        is TranslationException.NotEnoughStorage -> context.stringResource(
            MR.strings.translation_error_storage_low,
            Formatter.formatShortFileSize(context, error.requiredBytes),
            Formatter.formatShortFileSize(context, error.availableBytes),
        )
        is TranslationException.StorageWriteFailed -> context.stringResource(MR.strings.translation_error_storage_write)
        is TranslationException.LocalSourceUnavailable -> context.stringResource(
            MR.strings.translation_error_local_unavailable,
        )
        is TranslationException.EmptyChapter -> context.stringResource(MR.strings.translation_error_empty_chapter)
        is TranslationException.CorruptChapter -> context.stringResource(MR.strings.translation_error_corrupt_chapter)
        // Page level errors normally only appear in the partial summary, but keep the mapping total.
        is TranslationException.RateLimited -> context.stringResource(MR.strings.translation_reason_rate_limited, 1)
        is TranslationException.ServerError -> context.stringResource(MR.strings.translation_reason_server, 1)
        is TranslationException.NetworkError -> context.stringResource(MR.strings.translation_reason_network, 1)
        is TranslationException.ContentBlocked -> context.stringResource(MR.strings.translation_reason_blocked, 1)
        is TranslationException.NoImageReturned -> context.stringResource(MR.strings.translation_reason_no_image, 1)
        is TranslationException.CorruptPage -> context.stringResource(MR.strings.translation_reason_corrupt, 1)
        is TranslationException.UnreadableAnswer -> context.stringResource(MR.strings.translation_reason_unreadable, 1)
        is TranslationException.OutputTruncated -> context.stringResource(
            MR.strings.translation_reason_output_truncated,
            1,
        )
    }
}
