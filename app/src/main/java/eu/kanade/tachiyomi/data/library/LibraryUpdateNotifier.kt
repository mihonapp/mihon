package eu.kanade.tachiyomi.data.library

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.os.bundleOf
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.transformations
import coil3.transform.CircleCropTransformation
import dev.zacsweers.metro.Inject
import eu.kanade.presentation.util.formatChapterNumber
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.data.download.Downloader
import eu.kanade.tachiyomi.data.notification.NotificationHandler
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.source.UnmeteredSource
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.lang.chop
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.getBitmapOrNull
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.notify
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tachiyomi.core.common.Constants
import tachiyomi.core.common.i18n.pluralStringResource
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.history.interactor.GetNextChapters
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.library.service.LibraryVisibility
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.action_cancel
import tachiyomi.i18n.action_download
import tachiyomi.i18n.action_mark_as_read
import tachiyomi.i18n.action_show_errors
import tachiyomi.i18n.action_view_chapters
import tachiyomi.i18n.app_name
import tachiyomi.i18n.komikita_continue_reading
import tachiyomi.i18n.label_warning
import tachiyomi.i18n.notification_chapters_generic
import tachiyomi.i18n.notification_chapters_multiple
import tachiyomi.i18n.notification_chapters_multiple_and_more
import tachiyomi.i18n.notification_chapters_single
import tachiyomi.i18n.notification_chapters_single_and_more
import tachiyomi.i18n.notification_new_chapters
import tachiyomi.i18n.notification_new_chapters_summary
import tachiyomi.i18n.notification_size_warning
import tachiyomi.i18n.notification_update_error
import tachiyomi.i18n.notification_updating_progress
import java.math.RoundingMode
import java.text.NumberFormat
import mihon.icons.materialsymbols.R as MaterialSymbolsR

@Inject
class LibraryUpdateNotifier(
    private val context: Context,
    private val securityPreferences: SecurityPreferences,
    private val sourceManager: SourceManager,
    private val visibility: LibraryVisibility,
    private val getNextChapters: GetNextChapters,
) {

    private val percentFormatter = NumberFormat.getPercentInstance().apply {
        roundingMode = RoundingMode.DOWN
        maximumFractionDigits = 0
    }

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

    private val progressNotificationBuilder by lazy { createProgressNotificationBuilder() }

    fun buildInitialProgressNotification(): Notification = createProgressNotificationBuilder().build()

    private fun createProgressNotificationBuilder() =
        context.notificationBuilder(Notifications.CHANNEL_LIBRARY_PROGRESS) {
            setContentTitle(context.stringResource(MR.strings.app_name))
            setSmallIcon(MaterialSymbolsR.drawable.rounded_refresh)
            setLargeIcon(notificationBitmap)
            setOngoing(true)
            setOnlyAlertOnce(true)
            addAction(
                MaterialSymbolsR.drawable.rounded_close,
                context.stringResource(MR.strings.action_cancel),
                cancelIntent,
            )
        }

    private val progressNotificationMutex = Mutex()
    private var lastProgressNotificationTime = 0L

    /**
     * Shows the notification containing the currently updating manga and the progress.
     *
     * Called from several update coroutines at once, so it shares the builder under a lock and skips updates that
     * come faster than the system would show them anyway.
     *
     * @param manga the manga that are being updated.
     * @param current the current progress.
     * @param total the total progress.
     */
    suspend fun showProgressNotification(manga: List<Manga>, current: Int, total: Int) {
        progressNotificationMutex.withLock {
            val now = SystemClock.elapsedRealtime()
            if (current < total && now - lastProgressNotificationTime < NOTIF_PROGRESS_INTERVAL_MS) return
            lastProgressNotificationTime = now

            val titles = if (manga.isNotEmpty() && !securityPreferences.hideNotificationContent.get()) {
                val hiddenIds = visibility.hiddenMangaIds()
                manga.filterNot { it.id in hiddenIds }.map { it.title.chop(NOTIF_TITLE_MAX_LEN) }
            } else {
                emptyList()
            }.takeIf { it.isNotEmpty() && !securityPreferences.hideNotificationContent.get() }

            context.notify(
                Notifications.ID_LIBRARY_PROGRESS,
                progressNotificationBuilder
                    .setContentTitle(
                        context.stringResource(
                            MR.strings.notification_updating_progress,
                            percentFormatter.format(current.toFloat() / total),
                        ),
                    )
                    .setContentText(titles?.joinToString(", "))
                    .setStyle(titles?.let { NotificationCompat.BigTextStyle().bigText(it.joinToString("\n")) })
                    .setProgress(total, current, false)
                    .build(),
            )
        }
    }

    /**
     * Warn when excessively checking any single source.
     */
    suspend fun showQueueSizeWarningNotificationIfNeeded(mangaToUpdate: List<LibraryManga>) {
        val maxUpdatesFromSource = mangaToUpdate
            .groupBy { it.manga.source }
            .filter { (sourceId, _) -> sourceManager.get(sourceId) !is UnmeteredSource }
            .maxOfOrNull { it.value.size } ?: 0

        if (maxUpdatesFromSource <= MANGA_PER_SOURCE_QUEUE_WARNING_THRESHOLD) {
            return
        }

        context.notify(
            Notifications.ID_LIBRARY_SIZE_WARNING,
            Notifications.CHANNEL_LIBRARY_PROGRESS,
        ) {
            setContentTitle(context.stringResource(MR.strings.label_warning))
            setStyle(
                NotificationCompat.BigTextStyle().bigText(context.stringResource(MR.strings.notification_size_warning)),
            )
            setSmallIcon(MaterialSymbolsR.drawable.rounded_filled_warning)
            setTimeoutAfter(Downloader.WARNING_NOTIF_TIMEOUT_MS)
            setContentIntent(NotificationHandler.openUrl(context, HELP_WARNING_URL))
        }
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

        context.notify(
            Notifications.ID_LIBRARY_ERROR,
            Notifications.CHANNEL_LIBRARY_ERROR,
        ) {
            setContentTitle(context.pluralStringResource(MR.plurals.notification_update_error, failed, failed))
            setContentText(context.stringResource(MR.strings.action_show_errors))
            setSmallIcon(R.drawable.ic_komikita)

            setContentIntent(NotificationReceiver.openErrorLogPendingActivity(context, uri))
        }
    }

    /**
     * Shows the notification containing the result of the update done by the service.
     *
     * @param rawUpdates a list of manga with new updates.
     */
    suspend fun showUpdateNotifications(rawUpdates: List<Pair<Manga, Array<Chapter>>>) {
        val hiddenIds = visibility.hiddenMangaIds()
        val candidates = rawUpdates.filterNot { it.first.id in hiddenIds }.filter { it.second.isNotEmpty() }
        // Cover loading can suspend. Check privacy again after it finishes and immediately before posting.
        val notifications = if (securityPreferences.hideNotificationContent.get()) {
            emptyList()
        } else {
            candidates.map { (manga, chapters) -> manga.id to createNewChaptersNotification(manga, chapters) }
        }
        chapterNotificationsMutex.withLock {
            val hiddenAtPost = visibility.hiddenMangaIds()
            val updates = candidates.filterNot { it.first.id in hiddenAtPost }
            if (updates.isEmpty()) return@withLock

            val hideDetails = securityPreferences.hideNotificationContent.get()
            val titles = if (hideDetails) emptyList() else updates.map { it.first.title.chop(NOTIF_TITLE_MAX_LEN) }
            postChapterSummary(updates.map { it.first.id }, titles)

            if (!hideDetails) {
                context.notify(
                    notifications.filterNot { it.first in hiddenAtPost }.map { (mangaId, notification) ->
                        NotificationManagerCompat.NotificationWithIdAndTag(mangaId.hashCode(), notification)
                    },
                )
            }
        }
    }

    internal suspend fun removeHiddenChapterNotifications(hiddenIds: Set<Long>) {
        chapterNotificationsMutex.withLock {
            val manager = NotificationManagerCompat.from(context)
            val group = manager.activeNotifications.filter { it.notification.group == Notifications.GROUP_NEW_CHAPTERS }
            val summary = group.firstOrNull { it.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0 }
            val privacy = ChapterNotificationPrivacy(hiddenIds)
            val (hidden, visible) = group.filterNot { it === summary }
                .map { posted ->
                    val mangaId = posted.notification.extras
                        ?.takeIf { it.containsKey(EXTRA_MANGA_ID) }
                        ?.getLong(EXTRA_MANGA_ID)
                    posted to mangaId
                }
                .partition { (posted, mangaId) -> privacy.isHidden(posted.id, mangaId) }

            hidden.forEach { (posted, _) -> manager.cancel(posted.tag, posted.id) }
            if (summary == null) return@withLock

            val summaryMangaIds = summary.notification.extras?.getLongArray(EXTRA_SUMMARY_MANGA_IDS)?.asList()
            when (val change = privacy.summaryChange(summaryMangaIds, visible.map { it.second }, hidden.isNotEmpty())) {
                ChapterNotificationPrivacy.SummaryChange.Cancel -> manager.cancel(summary.tag, summary.id)
                is ChapterNotificationPrivacy.SummaryChange.Replace -> postChapterSummary(
                    mangaIds = change.mangaIds,
                    titles = emptyList(),
                    mangaCount = change.mangaCount,
                    previousTime = summary.notification.`when`,
                )
                null -> Unit
            }
        }
    }

    private fun postChapterSummary(
        mangaIds: List<Long>,
        titles: List<String>,
        mangaCount: Int = mangaIds.size,
        previousTime: Long? = null,
    ) {
        val summary = mangaCount.takeIf { it > 0 }?.let {
            context.pluralStringResource(MR.plurals.notification_new_chapters_summary, it, it)
        }
        context.notify(
            Notifications.ID_NEW_CHAPTERS,
            Notifications.CHANNEL_NEW_CHAPTERS,
        ) {
            addExtras(bundleOf(EXTRA_SUMMARY_MANGA_IDS to mangaIds.toLongArray()))
            setContentTitle(context.stringResource(MR.strings.notification_new_chapters))
            setContentText(if (titles.isEmpty()) summary else titles.joinToString(", "))
            if (titles.size > 1) {
                setStyle(NotificationCompat.BigTextStyle().bigText(titles.joinToString("\n")).setSummaryText(summary))
            }

            setSmallIcon(R.drawable.ic_komikita)
            setLargeIcon(notificationBitmap)

            setGroup(Notifications.GROUP_NEW_CHAPTERS)
            setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
            setGroupSummary(true)
            priority = NotificationCompat.PRIORITY_HIGH

            setContentIntent(getNotificationIntent())
            setAutoCancel(true)
            if (previousTime != null) {
                setWhen(previousTime)
                setOnlyAlertOnce(true)
            }
        }
    }

    private suspend fun createNewChaptersNotification(manga: Manga, chapters: Array<Chapter>): Notification {
        val icon = getMangaIcon(manga)
        val nextChapter = getNextChapters.await(manga.id).firstOrNull()
        return context.notificationBuilder(Notifications.CHANNEL_NEW_CHAPTERS) {
            addExtras(bundleOf(EXTRA_MANGA_ID to manga.id))
            setContentTitle(manga.title)

            val description = getNewChaptersDescription(chapters)
            setContentText(description)
            setStyle(NotificationCompat.BigTextStyle().bigText(description))

            setSmallIcon(R.drawable.ic_komikita)

            if (icon != null) {
                setLargeIcon(icon)
            }

            setGroup(Notifications.GROUP_NEW_CHAPTERS)
            setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
            priority = NotificationCompat.PRIORITY_HIGH

            // Open first chapter on tap
            setContentIntent(NotificationReceiver.openChapterPendingActivity(context, manga, chapters.first()))
            setAutoCancel(true)

            if (nextChapter != null) {
                addAction(
                    MaterialSymbolsR.drawable.rounded_book,
                    context.stringResource(MR.strings.komikita_continue_reading),
                    NotificationReceiver.openChapterPendingActivity(context, manga, nextChapter),
                )
            }

            // Mark chapters as read action
            addAction(
                MaterialSymbolsR.drawable.rounded_check,
                context.stringResource(MR.strings.action_mark_as_read),
                NotificationReceiver.markAsReadPendingBroadcast(
                    context,
                    manga,
                    chapters,
                    Notifications.ID_NEW_CHAPTERS,
                ),
            )
            // Android shows up to three actions; keep Download alongside Continue and Mark as read.
            if (nextChapter == null) {
                addAction(
                    MaterialSymbolsR.drawable.rounded_book,
                    context.stringResource(MR.strings.action_view_chapters),
                    NotificationReceiver.openChapterPendingActivity(
                        context,
                        manga,
                        Notifications.ID_NEW_CHAPTERS,
                    ),
                )
            }
            // Download chapters action
            // Only add the action when chapters is within threshold
            if (chapters.size <= Downloader.CHAPTERS_PER_SOURCE_QUEUE_WARNING_THRESHOLD) {
                addAction(
                    android.R.drawable.stat_sys_download_done,
                    context.stringResource(MR.strings.action_download),
                    NotificationReceiver.downloadChaptersPendingBroadcast(
                        context,
                        manga,
                        chapters,
                        Notifications.ID_NEW_CHAPTERS,
                    ),
                )
            }
        }.build()
    }

    /**
     * Cancels the progress notification.
     */
    fun cancelProgressNotification() {
        context.cancelNotification(Notifications.ID_LIBRARY_PROGRESS)
    }

    private suspend fun getMangaIcon(manga: Manga): Bitmap? {
        val request = ImageRequest.Builder(context)
            .data(manga)
            .transformations(CircleCropTransformation())
            .size(NOTIF_ICON_SIZE)
            .build()
        val drawable = context.imageLoader.execute(request).image?.asDrawable(context.resources)
        return drawable?.getBitmapOrNull()
    }

    private fun getNewChaptersDescription(chapters: Array<Chapter>): String {
        val displayableChapterNumbers = chapters
            .filter { it.isRecognizedNumber }
            .sortedBy { it.chapterNumber }
            .map { formatChapterNumber(it.chapterNumber) }
            .toSet()

        return when (displayableChapterNumbers.size) {
            // No sensible chapter numbers to show (i.e. no chapters have parsed chapter number)
            0 -> {
                // "1 new chapter" or "5 new chapters"
                context.pluralStringResource(
                    MR.plurals.notification_chapters_generic,
                    chapters.size,
                    chapters.size,
                )
            }
            // Only 1 chapter has a parsed chapter number
            1 -> {
                val remaining = chapters.size - displayableChapterNumbers.size
                if (remaining == 0) {
                    // "Chapter 2.5"
                    context.stringResource(
                        MR.strings.notification_chapters_single,
                        displayableChapterNumbers.first(),
                    )
                } else {
                    // "Chapter 2.5 and 10 more"
                    context.stringResource(
                        MR.strings.notification_chapters_single_and_more,
                        displayableChapterNumbers.first(),
                        remaining,
                    )
                }
            }
            // Everything else (i.e. multiple parsed chapter numbers)
            else -> {
                val shouldTruncate = displayableChapterNumbers.size > NOTIF_MAX_CHAPTERS
                if (shouldTruncate) {
                    // "Chapters 1, 2.5, 3, 4, 5 and 10 more"
                    val remaining = displayableChapterNumbers.size - NOTIF_MAX_CHAPTERS
                    val joinedChapterNumbers = displayableChapterNumbers
                        .take(NOTIF_MAX_CHAPTERS)
                        .joinToString(", ")
                    context.pluralStringResource(
                        MR.plurals.notification_chapters_multiple_and_more,
                        remaining,
                        joinedChapterNumbers,
                        remaining,
                    )
                } else {
                    // "Chapters 1, 2.5, 3"
                    context.stringResource(
                        MR.strings.notification_chapters_multiple,
                        displayableChapterNumbers.joinToString(", "),
                    )
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
            action = Constants.SHORTCUT_UPDATES
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val HELP_WARNING_URL =
            "https://mihon.app/docs/faq/library#why-am-i-warned-about-large-bulk-updates-and-downloads"

        private const val EXTRA_MANGA_ID = "chapter_manga_id"
        private const val EXTRA_SUMMARY_MANGA_IDS = "chapter_summary_manga_ids"
        private val chapterNotificationsMutex = Mutex()
    }
}

private const val NOTIF_MAX_CHAPTERS = 5
private const val NOTIF_TITLE_MAX_LEN = 45
private const val NOTIF_ICON_SIZE = 192
private const val MANGA_PER_SOURCE_QUEUE_WARNING_THRESHOLD = 60
private const val NOTIF_PROGRESS_INTERVAL_MS = 500L
