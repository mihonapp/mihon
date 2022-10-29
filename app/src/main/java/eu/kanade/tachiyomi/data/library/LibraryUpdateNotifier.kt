package eu.kanade.tachiyomi.data.library

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import coil.imageLoader
import coil.request.ImageRequest
import coil.transform.CircleCropTransformation
import eu.kanade.domain.chapter.model.Chapter
import eu.kanade.domain.manga.model.Manga
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.data.download.Downloader
import eu.kanade.tachiyomi.data.notification.NotificationHandler
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.lang.chop
import eu.kanade.tachiyomi.util.lang.launchUI
import eu.kanade.tachiyomi.util.system.notification
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.notificationManager
import uy.kohesive.injekt.injectLazy
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols

class LibraryUpdateNotifier(private val context: Context) {

    private val preferences: SecurityPreferences by injectLazy()

    /**
     * Pending intent of action that cancels the library update
     */
    private val cancelIntent by lazy {
        NotificationReceiver.cancelLibraryUpdatePendingBroadcast(context)
    }

    /**
     * Bitmap of the app for notifications.
     */
    private val notificationBitmap by lazy {
        BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
    }

    /**
     * Cached progress notification to avoid creating a lot.
     */
    val progressNotificationBuilder by lazy {
        context.notificationBuilder(Notifications.CHANNEL_LIBRARY_PROGRESS) {
            setContentTitle(context.getString(R.string.app_name))
            setSmallIcon(R.drawable.ic_refresh_24dp)
            setLargeIcon(notificationBitmap)
            setOngoing(true)
            setOnlyAlertOnce(true)
            addAction(R.drawable.ic_close_24dp, context.getString(R.string.action_cancel), cancelIntent)
        }
    }

    /**
     * Shows the notification containing the currently updating manga and the progress.
     *
     * @param manga the manga that are being updated.
     * @param current the current progress.
     * @param total the total progress.
     */
    fun showProgressNotification(manga: List<Manga>, current: Int, total: Int) {
        if (preferences.hideNotificationContent().get()) {
            progressNotificationBuilder
                .setContentTitle(context.getString(R.string.notification_check_updates))
                .setContentText("($current/$total)")
        } else {
            val updatingText = manga.joinToString("\n") { it.title.chop(40) }
            progressNotificationBuilder
                .setContentTitle(context.getString(R.string.notification_updating, current, total))
                .setStyle(NotificationCompat.BigTextStyle().bigText(updatingText))
        }

        context.notificationManager.notify(
            Notifications.ID_LIBRARY_PROGRESS,
            progressNotificationBuilder
                .setProgress(total, current, false)
                .build(),
        )
    }

    fun showQueueSizeWarningNotification() {
        val notificationBuilder = context.notificationBuilder(Notifications.CHANNEL_LIBRARY_PROGRESS) {
            setContentTitle(context.getString(R.string.label_warning))
            setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.notification_size_warning)))
            setSmallIcon(R.drawable.ic_warning_white_24dp)
            setTimeoutAfter(Downloader.WARNING_NOTIF_TIMEOUT_MS)
            setContentIntent(NotificationHandler.openUrl(context, HELP_WARNING_URL))
        }

        context.notificationManager.notify(
            Notifications.ID_LIBRARY_SIZE_WARNING,
            notificationBuilder.build(),
        )
    }

    /**
     * Shows notification containing update entries that failed with action to open full log.
     *
     * @param failed Number of entries that failed to update.
     * @param uri Uri for error log file containing all titles that failed.
     */
    fun showUpdateErrorNotification(failed: Int, uri: Uri) {
        if (failed == 0) {
            return
        }

        context.notificationManager.notify(
            Notifications.ID_LIBRARY_ERROR,
            context.notificationBuilder(Notifications.CHANNEL_LIBRARY_ERROR) {
                setContentTitle(context.resources.getString(R.string.notification_update_error, failed))
                setContentText(context.getString(R.string.action_show_errors))
                setSmallIcon(R.drawable.ic_tachi)

                setContentIntent(NotificationReceiver.openErrorLogPendingActivity(context, uri))
            }
                .build(),
        )
    }

    /**
     * Shows notification containing update entries that were skipped.
     *
     * @param skipped Number of entries that were skipped during the update.
     */
    fun showUpdateSkippedNotification(skipped: Int) {
        if (skipped == 0) {
            return
        }

        context.notificationManager.notify(
            Notifications.ID_LIBRARY_SKIPPED,
            context.notificationBuilder(Notifications.CHANNEL_LIBRARY_SKIPPED) {
                setContentTitle(context.resources.getString(R.string.notification_update_skipped, skipped))
                setContentText(context.getString(R.string.learn_more))
                setSmallIcon(R.drawable.ic_tachi)
                setContentIntent(NotificationHandler.openUrl(context, HELP_SKIPPED_URL))
            }
                .build(),
        )
    }

    /**
     * Shows the notification containing the result of the update done by the service.
     *
     * @param updates a list of manga with new updates.
     */
    fun showUpdateNotifications(updates: List<Pair<Manga, Array<Chapter>>>) {
        NotificationManagerCompat.from(context).apply {
            // Parent group notification
            notify(
                Notifications.ID_NEW_CHAPTERS,
                context.notification(Notifications.CHANNEL_NEW_CHAPTERS) {
                    setContentTitle(context.getString(R.string.notification_new_chapters))
                    if (updates.size == 1 && !preferences.hideNotificationContent().get()) {
                        setContentText(updates.first().first.title.chop(NOTIF_TITLE_MAX_LEN))
                    } else {
                        setContentText(context.resources.getQuantityString(R.plurals.notification_new_chapters_summary, updates.size, updates.size))

                        if (!preferences.hideNotificationContent().get()) {
                            setStyle(
                                NotificationCompat.BigTextStyle().bigText(
                                    updates.joinToString("\n") {
                                        it.first.title.chop(NOTIF_TITLE_MAX_LEN)
                                    },
                                ),
                            )
                        }
                    }

                    setSmallIcon(R.drawable.ic_tachi)
                    setLargeIcon(notificationBitmap)

                    setGroup(Notifications.GROUP_NEW_CHAPTERS)
                    setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
                    setGroupSummary(true)
                    priority = NotificationCompat.PRIORITY_HIGH

                    setContentIntent(getNotificationIntent())
                    setAutoCancel(true)
                },
            )

            // Per-manga notification
            if (!preferences.hideNotificationContent().get()) {
                launchUI {
                    updates.forEach { (manga, chapters) ->
                        notify(manga.id.hashCode(), createNewChaptersNotification(manga, chapters))
                    }
                }
            }
        }
    }

    private suspend fun createNewChaptersNotification(manga: Manga, chapters: Array<Chapter>): Notification {
        val icon = getMangaIcon(manga)
        return context.notification(Notifications.CHANNEL_NEW_CHAPTERS) {
            setContentTitle(manga.title)

            val description = getNewChaptersDescription(chapters)
            setContentText(description)
            setStyle(NotificationCompat.BigTextStyle().bigText(description))

            setSmallIcon(R.drawable.ic_tachi)

            if (icon != null) {
                setLargeIcon(icon)
            }

            setGroup(Notifications.GROUP_NEW_CHAPTERS)
            setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
            priority = NotificationCompat.PRIORITY_HIGH

            // Open first chapter on tap
            setContentIntent(NotificationReceiver.openChapterPendingActivity(context, manga, chapters.first()))
            setAutoCancel(true)

            // Mark chapters as read action
            addAction(
                R.drawable.ic_glasses_24dp,
                context.getString(R.string.action_mark_as_read),
                NotificationReceiver.markAsReadPendingBroadcast(
                    context,
                    manga,
                    chapters,
                    Notifications.ID_NEW_CHAPTERS,
                ),
            )
            // View chapters action
            addAction(
                R.drawable.ic_book_24dp,
                context.getString(R.string.action_view_chapters),
                NotificationReceiver.openChapterPendingActivity(
                    context,
                    manga,
                    Notifications.ID_NEW_CHAPTERS,
                ),
            )
            // Download chapters action
            // Only add the action when chapters is within threshold
            if (chapters.size <= Downloader.CHAPTERS_PER_SOURCE_QUEUE_WARNING_THRESHOLD) {
                addAction(
                    android.R.drawable.stat_sys_download_done,
                    context.getString(R.string.action_download),
                    NotificationReceiver.downloadChaptersPendingBroadcast(
                        context,
                        manga,
                        chapters,
                        Notifications.ID_NEW_CHAPTERS,
                    ),
                )
            }
        }
    }

    /**
     * Cancels the progress notification.
     */
    fun cancelProgressNotification() {
        context.notificationManager.cancel(Notifications.ID_LIBRARY_PROGRESS)
    }

    private suspend fun getMangaIcon(manga: Manga): Bitmap? {
        val request = ImageRequest.Builder(context)
            .data(manga)
            .transformations(CircleCropTransformation())
            .size(NOTIF_ICON_SIZE)
            .build()
        val drawable = context.imageLoader.execute(request).drawable
        return (drawable as? BitmapDrawable)?.bitmap
    }

    private fun getNewChaptersDescription(chapters: Array<Chapter>): String {
        val formatter = DecimalFormat(
            "#.###",
            DecimalFormatSymbols()
                .apply { decimalSeparator = '.' },
        )

        val displayableChapterNumbers = chapters
            .filter { it.isRecognizedNumber }
            .sortedBy { it.chapterNumber }
            .map { formatter.format(it.chapterNumber) }
            .toSet()

        return when (displayableChapterNumbers.size) {
            // No sensible chapter numbers to show (i.e. no chapters have parsed chapter number)
            0 -> {
                // "1 new chapter" or "5 new chapters"
                context.resources.getQuantityString(R.plurals.notification_chapters_generic, chapters.size, chapters.size)
            }
            // Only 1 chapter has a parsed chapter number
            1 -> {
                val remaining = chapters.size - displayableChapterNumbers.size
                if (remaining == 0) {
                    // "Chapter 2.5"
                    context.resources.getString(R.string.notification_chapters_single, displayableChapterNumbers.first())
                } else {
                    // "Chapter 2.5 and 10 more"
                    context.resources.getString(R.string.notification_chapters_single_and_more, displayableChapterNumbers.first(), remaining)
                }
            }
            // Everything else (i.e. multiple parsed chapter numbers)
            else -> {
                val shouldTruncate = displayableChapterNumbers.size > NOTIF_MAX_CHAPTERS
                if (shouldTruncate) {
                    // "Chapters 1, 2.5, 3, 4, 5 and 10 more"
                    val remaining = displayableChapterNumbers.size - NOTIF_MAX_CHAPTERS
                    val joinedChapterNumbers = displayableChapterNumbers.take(NOTIF_MAX_CHAPTERS).joinToString(", ")
                    context.resources.getQuantityString(R.plurals.notification_chapters_multiple_and_more, remaining, joinedChapterNumbers, remaining)
                } else {
                    // "Chapters 1, 2.5, 3"
                    context.resources.getString(R.string.notification_chapters_multiple, displayableChapterNumbers.joinToString(", "))
                }
            }
        }
    }

    /**
     * Returns an intent to open the main activity.
     */
    private fun getNotificationIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            action = MainActivity.SHORTCUT_RECENTLY_UPDATED
        }
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        const val HELP_WARNING_URL = "https://tachiyomi.org/help/faq/#why-does-the-app-warn-about-large-bulk-updates-and-downloads"
    }
}

private const val NOTIF_MAX_CHAPTERS = 5
private const val NOTIF_TITLE_MAX_LEN = 45
private const val NOTIF_ICON_SIZE = 192
private const val HELP_SKIPPED_URL = "https://tachiyomi.org/help/faq/#why-does-global-update-skip-some-entries"
