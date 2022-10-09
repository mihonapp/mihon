package eu.kanade.tachiyomi.data.download

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import eu.kanade.domain.download.service.DownloadPreferences
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.lang.withUIContext
import eu.kanade.tachiyomi.util.system.acquireWakeLock
import eu.kanade.tachiyomi.util.system.isConnectedToWifi
import eu.kanade.tachiyomi.util.system.isOnline
import eu.kanade.tachiyomi.util.system.isServiceRunning
import eu.kanade.tachiyomi.util.system.logcat
import eu.kanade.tachiyomi.util.system.notification
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import logcat.LogPriority
import ru.beryukhov.reactivenetwork.ReactiveNetwork
import uy.kohesive.injekt.injectLazy

/**
 * This service is used to manage the downloader. The system can decide to stop the service, in
 * which case the downloader is also stopped. It's also stopped while there's no network available.
 * While the downloader is running, a wake lock will be held.
 */
class DownloadService : Service() {

    companion object {

        private val _isRunning = MutableStateFlow(false)
        val isRunning = _isRunning.asStateFlow()

        /**
         * Starts this service.
         *
         * @param context the application context.
         */
        fun start(context: Context) {
            val intent = Intent(context, DownloadService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        /**
         * Stops this service.
         *
         * @param context the application context.
         */
        fun stop(context: Context) {
            context.stopService(Intent(context, DownloadService::class.java))
        }

        /**
         * Returns the status of the service.
         *
         * @param context the application context.
         * @return true if the service is running, false otherwise.
         */
        fun isRunning(context: Context): Boolean {
            return context.isServiceRunning(DownloadService::class.java)
        }
    }

    private val downloadManager: DownloadManager by injectLazy()
    private val downloadPreferences: DownloadPreferences by injectLazy()

    /**
     * Wake lock to prevent the device to enter sleep mode.
     */
    private lateinit var wakeLock: PowerManager.WakeLock

    private lateinit var ioScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        startForeground(Notifications.ID_DOWNLOAD_CHAPTER_PROGRESS, getPlaceholderNotification())
        wakeLock = acquireWakeLock(javaClass.name)
        _isRunning.value = true
        listenDownloaderState()
        listenNetworkChanges()
    }

    override fun onDestroy() {
        ioScope?.cancel()
        _isRunning.value = false
        downloadManager.stopDownloads()
        wakeLock.releaseIfHeld()
        super.onDestroy()
    }

    // Not used
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    // Not used
    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private fun stopDownloads(@StringRes string: Int) {
        downloadManager.stopDownloads(getString(string))
    }

    private fun listenNetworkChanges() {
        ReactiveNetwork()
            .observeNetworkConnectivity(applicationContext)
            .onEach {
                withUIContext {
                    if (isOnline()) {
                        if (downloadPreferences.downloadOnlyOverWifi().get() && !isConnectedToWifi()) {
                            stopDownloads(R.string.download_notifier_text_only_wifi)
                        } else {
                            val started = downloadManager.startDownloads()
                            if (!started) stopSelf()
                        }
                    } else {
                        stopDownloads(R.string.download_notifier_no_network)
                    }
                }
            }
            .catch { error ->
                withUIContext {
                    logcat(LogPriority.ERROR, error)
                    toast(R.string.download_queue_error)
                    stopSelf()
                }
            }
            .launchIn(ioScope)
    }

    /**
     * Listens to downloader status. Enables or disables the wake lock depending on the status.
     */
    private fun listenDownloaderState() {
        _isRunning
            .onEach { isRunning ->
                if (isRunning) {
                    wakeLock.acquireIfNotHeld()
                } else {
                    wakeLock.releaseIfHeld()
                }
            }
            .catch {
                // Ignore errors
            }
            .launchIn(ioScope)
    }

    private fun PowerManager.WakeLock.releaseIfHeld() {
        if (isHeld) release()
    }

    private fun PowerManager.WakeLock.acquireIfNotHeld() {
        if (!isHeld) acquire()
    }

    private fun getPlaceholderNotification(): Notification {
        return notification(Notifications.CHANNEL_DOWNLOADER_PROGRESS) {
            setContentTitle(getString(R.string.download_notifier_downloader_title))
        }
    }
}
