package eu.kanade.tachiyomi.data.download

import android.content.Context
import android.support.v4.app.NotificationCompat
import eu.kanade.tachiyomi.Constants
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.decodeSampledBitmap
import eu.kanade.tachiyomi.util.notificationManager
import java.io.File


class ImageNotifier(private val context: Context) {
    /**
     * Notification builder.
     */
    private val notificationBuilder = NotificationCompat.Builder(context)

    /**
     * Id of the notification.
     */
    private val notificationId: Int
        get() = Constants.NOTIFICATION_DOWNLOAD_IMAGE_ID

    /**
     * Status of download. Used for correct notification icon.
     */
    private var isDownloading = false

    /**
     * Called when download progress changes.
     * @param progress progress value in range [0,100]
     */
    fun onProgressChange(progress: Int) {
        with(notificationBuilder) {
            if (!isDownloading) {
                setContentTitle(context.getString(R.string.saving_picture))
                setSmallIcon(android.R.drawable.stat_sys_download)
                setLargeIcon(null)
                setStyle(null)
                // Clear old actions if they exist
                if (!mActions.isEmpty())
                    mActions.clear()
                isDownloading = true
            }

            setProgress(100, progress, false)
        }
        // Displays the progress bar on notification
        context.notificationManager.notify(notificationId, notificationBuilder.build())
    }

    /**
     * Called when image download is complete
     * @param file image file containing downloaded page image
     */
    fun onComplete(file: File) {
        with(notificationBuilder) {
            if (isDownloading) {
                setProgress(0, 0, false)
                isDownloading = false
            }
            setContentTitle(context.getString(R.string.picture_saved))
            setSmallIcon(R.drawable.ic_insert_photo_black_24dp)
            setLargeIcon(file.decodeSampledBitmap(100, 100))
            setStyle(NotificationCompat.BigPictureStyle().bigPicture(file.decodeSampledBitmap(1024, 1024)))
            setAutoCancel(true)

            // Clear old actions if they exist
            if (!mActions.isEmpty())
                mActions.clear()

            setContentIntent(ImageNotificationReceiver.showImageIntent(context, file.absolutePath))
            // Share action
            addAction(R.drawable.ic_share_white_24dp,
                    context.getString(R.string.action_share),
                    ImageNotificationReceiver.shareImageIntent(context, file.absolutePath, notificationId))
            // Delete action
            addAction(R.drawable.ic_delete_white_24dp,
                    context.getString(R.string.action_delete),
                    ImageNotificationReceiver.deleteImageIntent(context, file.absolutePath, notificationId))
        }
        // Displays the progress bar on notification
        context.notificationManager.notify(notificationId, notificationBuilder.build())
    }

    /**
     * Clears the notification message
     */
    internal fun onClear() {
        context.notificationManager.cancel(notificationId)
    }


    /**
     * Called on error while downloading image
     * @param error string containing error information
     */
    internal fun onError(error: String?) {
        // Create notification
        with(notificationBuilder) {
            setContentTitle(context.getString(R.string.download_notifier_title_error))
            setContentText(error ?: context.getString(R.string.download_notifier_unkown_error))
            setSmallIcon(android.R.drawable.ic_menu_report_image)
            setProgress(0, 0, false)
        }
        context.notificationManager.notify(notificationId, notificationBuilder.build())
        isDownloading = false
    }

}
