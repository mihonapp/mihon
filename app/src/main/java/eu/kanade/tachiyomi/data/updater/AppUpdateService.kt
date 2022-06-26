package eu.kanade.tachiyomi.data.updater

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.content.ContextCompat
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.ProgressListener
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.newCallWithProgress
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.storage.getUriCompat
import eu.kanade.tachiyomi.util.storage.saveTo
import eu.kanade.tachiyomi.util.system.acquireWakeLock
import eu.kanade.tachiyomi.util.system.isServiceRunning
import eu.kanade.tachiyomi.util.system.logcat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import logcat.LogPriority
import okhttp3.Call
import okhttp3.internal.http2.ErrorCode
import okhttp3.internal.http2.StreamResetException
import uy.kohesive.injekt.injectLazy
import java.io.File

class AppUpdateService : Service() {

    private val network: NetworkHelper by injectLazy()

    /**
     * Wake lock that will be held until the service is destroyed.
     */
    private lateinit var wakeLock: PowerManager.WakeLock

    private lateinit var notifier: AppUpdateNotifier

    private var runningJob: Job? = null
    private var runningCall: Call? = null

    override fun onCreate() {
        super.onCreate()

        notifier = AppUpdateNotifier(this)
        wakeLock = acquireWakeLock(javaClass.name)

        startForeground(Notifications.ID_APP_UPDATER, notifier.onDownloadStarted().build())
    }

    /**
     * This method needs to be implemented, but it's not used/needed.
     */
    override fun onBind(intent: Intent): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        val url = intent.getStringExtra(EXTRA_DOWNLOAD_URL) ?: return START_NOT_STICKY
        val title = intent.getStringExtra(EXTRA_DOWNLOAD_TITLE) ?: getString(R.string.app_name)

        runningJob = launchIO {
            downloadApk(title, url)
        }

        runningJob?.invokeOnCompletion { stopSelf(startId) }
        return START_NOT_STICKY
    }

    override fun stopService(name: Intent?): Boolean {
        destroyJob()
        return super.stopService(name)
    }

    override fun onDestroy() {
        destroyJob()
        super.onDestroy()
    }

    private fun destroyJob() {
        runningJob?.cancel()
        runningCall?.cancel()
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
    }

    /**
     * Called to start downloading apk of new update
     *
     * @param url url location of file
     */
    private suspend fun downloadApk(title: String, url: String) {
        // Show notification download starting.
        notifier.onDownloadStarted(title)

        val progressListener = object : ProgressListener {
            // Progress of the download
            var savedProgress = 0

            // Keep track of the last notification sent to avoid posting too many.
            var lastTick = 0L

            override fun update(bytesRead: Long, contentLength: Long, done: Boolean) {
                val progress = (100 * (bytesRead.toFloat() / contentLength)).toInt()
                val currentTime = System.currentTimeMillis()
                if (progress > savedProgress && currentTime - 200 > lastTick) {
                    savedProgress = progress
                    lastTick = currentTime
                    notifier.onProgressChange(progress)
                }
            }
        }

        try {
            // Download the new update.
            val call = network.client.newCallWithProgress(GET(url), progressListener)
            runningCall = call
            val response = call.await()

            // File where the apk will be saved.
            val apkFile = File(externalCacheDir, "update.apk")

            if (response.isSuccessful) {
                response.body!!.source().saveTo(apkFile)
            } else {
                response.close()
                throw Exception("Unsuccessful response")
            }
            notifier.promptInstall(apkFile.getUriCompat(this))
        } catch (error: Exception) {
            logcat(LogPriority.ERROR, error)
            if (error is CancellationException ||
                (error is StreamResetException && error.errorCode == ErrorCode.CANCEL)
            ) {
                notifier.cancel()
            } else {
                notifier.onDownloadError(url)
            }
        }
    }

    companion object {

        internal const val EXTRA_DOWNLOAD_URL = "${BuildConfig.APPLICATION_ID}.UpdaterService.DOWNLOAD_URL"
        internal const val EXTRA_DOWNLOAD_TITLE = "${BuildConfig.APPLICATION_ID}.UpdaterService.DOWNLOAD_TITLE"

        /**
         * Returns the status of the service.
         *
         * @param context the application context.
         * @return true if the service is running, false otherwise.
         */
        private fun isRunning(context: Context): Boolean =
            context.isServiceRunning(AppUpdateService::class.java)

        /**
         * Downloads a new update and let the user install the new version from a notification.
         *
         * @param context the application context.
         * @param url the url to the new update.
         */
        fun start(context: Context, url: String, title: String? = context.getString(R.string.app_name)) {
            if (!isRunning(context)) {
                val intent = Intent(context, AppUpdateService::class.java).apply {
                    putExtra(EXTRA_DOWNLOAD_TITLE, title)
                    putExtra(EXTRA_DOWNLOAD_URL, url)
                }
                ContextCompat.startForegroundService(context, intent)
            }
        }

        /**
         * Stops the service.
         *
         * @param context the application context
         */
        fun stop(context: Context) {
            context.stopService(Intent(context, AppUpdateService::class.java))
        }

        /**
         * Returns [PendingIntent] that starts a service which downloads the apk specified in url.
         *
         * @param url the url to the new update.
         * @return [PendingIntent]
         */
        internal fun downloadApkPendingService(context: Context, url: String): PendingIntent {
            val intent = Intent(context, AppUpdateService::class.java).apply {
                putExtra(EXTRA_DOWNLOAD_URL, url)
            }
            return PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }
}
