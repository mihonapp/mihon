package eu.kanade.tachiyomi.data.notification

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.NotificationManagerCompat.IMPORTANCE_DEFAULT
import androidx.core.app.NotificationManagerCompat.IMPORTANCE_HIGH
import androidx.core.app.NotificationManagerCompat.IMPORTANCE_LOW
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.system.buildNotificationChannel
import eu.kanade.tachiyomi.util.system.buildNotificationChannelGroup

/**
 * Class to manage the basic information of all the notifications used in the app.
 */
object Notifications {

    /**
     * Common notification channel and ids used anywhere.
     */
    const val CHANNEL_COMMON = "common_channel"
    const val ID_UPDATER = 1
    const val ID_DOWNLOAD_IMAGE = 2

    /**
     * Notification channel and ids used by the library updater.
     */
    const val CHANNEL_LIBRARY = "library_channel"
    const val ID_LIBRARY_PROGRESS = -101
    const val ID_LIBRARY_ERROR = -102

    /**
     * Notification channel and ids used by the downloader.
     */
    private const val GROUP_DOWNLOADER = "group_downloader"
    const val CHANNEL_DOWNLOADER_PROGRESS = "downloader_progress_channel"
    const val ID_DOWNLOAD_CHAPTER_PROGRESS = -201
    const val CHANNEL_DOWNLOADER_COMPLETE = "downloader_complete_channel"
    const val ID_DOWNLOAD_CHAPTER_COMPLETE = -203
    const val CHANNEL_DOWNLOADER_ERROR = "downloader_error_channel"
    const val ID_DOWNLOAD_CHAPTER_ERROR = -202

    /**
     * Notification channel and ids used by the library updater.
     */
    const val CHANNEL_NEW_CHAPTERS = "new_chapters_channel"
    const val ID_NEW_CHAPTERS = -301
    const val GROUP_NEW_CHAPTERS = "eu.kanade.tachiyomi.NEW_CHAPTERS"

    /**
     * Notification channel and ids used by the library updater.
     */
    const val CHANNEL_UPDATES_TO_EXTS = "updates_ext_channel"
    const val ID_UPDATES_TO_EXTS = -401

    /**
     * Notification channel and ids used by the backup/restore system.
     */
    private const val GROUP_BACKUP_RESTORE = "group_backup_restore"
    const val CHANNEL_BACKUP_RESTORE_PROGRESS = "backup_restore_progress_channel"
    const val ID_BACKUP_PROGRESS = -501
    const val ID_RESTORE_PROGRESS = -503
    const val CHANNEL_BACKUP_RESTORE_COMPLETE = "backup_restore_complete_channel_v2"
    const val ID_BACKUP_COMPLETE = -502
    const val ID_RESTORE_COMPLETE = -504

    /**
     * Notification channel used for crash log file sharing.
     */
    const val CHANNEL_CRASH_LOGS = "crash_logs_channel"
    const val ID_CRASH_LOGS = -601

    /**
     * Notification channel used for Incognito Mode
     */
    const val CHANNEL_INCOGNITO_MODE = "incognito_mode_channel"
    const val ID_INCOGNITO_MODE = -701

    private val deprecatedChannels = listOf(
        "downloader_channel",
        "backup_restore_complete_channel"
    )

    /**
     * Creates the notification channels introduced in Android Oreo.
     * This won't do anything on Android versions that don't support notification channels.
     *
     * @param context The application context.
     */
    fun createChannels(context: Context) {
        val notificationService = NotificationManagerCompat.from(context)

        val channelGroupList = listOf(
            buildNotificationChannelGroup(GROUP_BACKUP_RESTORE) {
                setName(context.getString(R.string.group_backup_restore))
            },
            buildNotificationChannelGroup(GROUP_DOWNLOADER) {
                setName(context.getString(R.string.group_downloader))
            }
        )
        notificationService.createNotificationChannelGroupsCompat(channelGroupList)

        val channelList = listOf(
            buildNotificationChannel(CHANNEL_COMMON, IMPORTANCE_LOW) {
                setName(context.getString(R.string.channel_common))
            },
            buildNotificationChannel(CHANNEL_LIBRARY, IMPORTANCE_LOW) {
                setName(context.getString(R.string.channel_library))
                setShowBadge(false)
            },
            buildNotificationChannel(CHANNEL_DOWNLOADER_PROGRESS, IMPORTANCE_LOW) {
                setName(context.getString(R.string.channel_progress))
                setGroup(GROUP_DOWNLOADER)
                setShowBadge(false)
            },
            buildNotificationChannel(CHANNEL_DOWNLOADER_COMPLETE, IMPORTANCE_LOW) {
                setName(context.getString(R.string.channel_complete))
                setGroup(GROUP_DOWNLOADER)
                setShowBadge(false)
            },
            buildNotificationChannel(CHANNEL_DOWNLOADER_ERROR, IMPORTANCE_LOW) {
                setName(context.getString(R.string.channel_errors))
                setGroup(GROUP_DOWNLOADER)
                setShowBadge(false)
            },
            buildNotificationChannel(CHANNEL_NEW_CHAPTERS, IMPORTANCE_DEFAULT) {
                setName(context.getString(R.string.channel_new_chapters))
            },
            buildNotificationChannel(CHANNEL_UPDATES_TO_EXTS, IMPORTANCE_DEFAULT) {
                setName(context.getString(R.string.channel_ext_updates))
            },
            buildNotificationChannel(CHANNEL_BACKUP_RESTORE_PROGRESS, IMPORTANCE_LOW) {
                setName(context.getString(R.string.channel_progress))
                setGroup(GROUP_BACKUP_RESTORE)
                setShowBadge(false)
            },
            buildNotificationChannel(CHANNEL_BACKUP_RESTORE_COMPLETE, IMPORTANCE_HIGH) {
                setName(context.getString(R.string.channel_complete))
                setGroup(GROUP_BACKUP_RESTORE)
                setShowBadge(false)
                setSound(null, null)
            },
            buildNotificationChannel(CHANNEL_CRASH_LOGS, IMPORTANCE_HIGH) {
                setName(context.getString(R.string.channel_crash_logs))
            },
            buildNotificationChannel(CHANNEL_INCOGNITO_MODE, IMPORTANCE_LOW) {
                setName(context.getString(R.string.pref_incognito_mode))
            },
        )
        notificationService.createNotificationChannelsCompat(channelList)

        // Delete old notification channels
        deprecatedChannels.forEach(notificationService::deleteNotificationChannel)
    }
}
