package eu.kanade.tachiyomi.extension.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.core.content.ContextCompat
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.LoadResult
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import logcat.LogPriority
import tachiyomi.core.util.lang.launchNow
import tachiyomi.core.util.system.logcat

/**
 * Broadcast receiver that listens for the system's packages installed, updated or removed, and only
 * notifies the given [listener] when the package is an extension.
 *
 * @param listener The listener that should be notified of extension installation events.
 */
internal class ExtensionInstallReceiver(private val listener: Listener) :
    BroadcastReceiver() {

    /**
     * Registers this broadcast receiver
     */
    fun register(context: Context) {
        ContextCompat.registerReceiver(context, this, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    /**
     * Returns the intent filter this receiver should subscribe to.
     */
    private val filter
        get() = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(ACTION_EXTENSION_ADDED)
            addAction(ACTION_EXTENSION_REPLACED)
            addAction(ACTION_EXTENSION_REMOVED)
            addDataScheme("package")
        }

    /**
     * Called when one of the events of the [filter] is received. When the package is an extension,
     * it's loaded in background and it notifies the [listener] when finished.
     */
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return

        when (intent.action) {
            Intent.ACTION_PACKAGE_ADDED, ACTION_EXTENSION_ADDED -> {
                if (isReplacing(intent)) return

                launchNow {
                    when (val result = getExtensionFromIntent(context, intent)) {
                        is LoadResult.Success -> listener.onExtensionInstalled(result.extension)
                        is LoadResult.Untrusted -> listener.onExtensionUntrusted(result.extension)
                        else -> {}
                    }
                }
            }
            Intent.ACTION_PACKAGE_REPLACED, ACTION_EXTENSION_REPLACED -> {
                launchNow {
                    when (val result = getExtensionFromIntent(context, intent)) {
                        is LoadResult.Success -> listener.onExtensionUpdated(result.extension)
                        // Not needed as a package can't be upgraded if the signature is different
                        // is LoadResult.Untrusted -> {}
                        else -> {}
                    }
                }
            }
            Intent.ACTION_PACKAGE_REMOVED, ACTION_EXTENSION_REMOVED -> {
                if (isReplacing(intent)) return

                val pkgName = getPackageNameFromIntent(intent)
                if (pkgName != null) {
                    listener.onPackageUninstalled(pkgName)
                }
            }
        }
    }

    /**
     * Returns true if this package is performing an update.
     *
     * @param intent The intent that triggered the event.
     */
    private fun isReplacing(intent: Intent): Boolean {
        return intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
    }

    /**
     * Returns the extension triggered by the given intent.
     *
     * @param context The application context.
     * @param intent The intent containing the package name of the extension.
     */
    private suspend fun getExtensionFromIntent(context: Context, intent: Intent?): LoadResult {
        val pkgName = getPackageNameFromIntent(intent)
        if (pkgName == null) {
            logcat(LogPriority.WARN) { "Package name not found" }
            return LoadResult.Error
        }
        return GlobalScope.async(Dispatchers.Default, CoroutineStart.DEFAULT) {
            ExtensionLoader.loadExtensionFromPkgName(context, pkgName)
        }.await()
    }

    /**
     * Returns the package name of the installed, updated or removed application.
     */
    private fun getPackageNameFromIntent(intent: Intent?): String? {
        return intent?.data?.encodedSchemeSpecificPart ?: return null
    }

    /**
     * Listener that receives extension installation events.
     */
    interface Listener {
        fun onExtensionInstalled(extension: Extension.Installed)
        fun onExtensionUpdated(extension: Extension.Installed)
        fun onExtensionUntrusted(extension: Extension.Untrusted)
        fun onPackageUninstalled(pkgName: String)
    }

    companion object {
        private const val ACTION_EXTENSION_ADDED = "${BuildConfig.APPLICATION_ID}.ACTION_EXTENSION_ADDED"
        private const val ACTION_EXTENSION_REPLACED = "${BuildConfig.APPLICATION_ID}.ACTION_EXTENSION_REPLACED"
        private const val ACTION_EXTENSION_REMOVED = "${BuildConfig.APPLICATION_ID}.ACTION_EXTENSION_REMOVED"

        fun notifyAdded(context: Context, pkgName: String) {
            notify(context, pkgName, ACTION_EXTENSION_ADDED)
        }

        fun notifyReplaced(context: Context, pkgName: String) {
            notify(context, pkgName, ACTION_EXTENSION_REPLACED)
        }

        fun notifyRemoved(context: Context, pkgName: String) {
            notify(context, pkgName, ACTION_EXTENSION_REMOVED)
        }

        private fun notify(context: Context, pkgName: String, action: String) {
            Intent(action).apply {
                data = Uri.parse("package:$pkgName")
                `package` = context.packageName
                context.sendBroadcast(this)
            }
        }
    }
}
