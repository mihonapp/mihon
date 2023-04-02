package eu.kanade.tachiyomi.util

import android.content.Context
import android.os.Build
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.util.storage.getUriCompat
import eu.kanade.tachiyomi.util.system.createFileInCacheDir
import eu.kanade.tachiyomi.util.system.toShareIntent
import eu.kanade.tachiyomi.util.system.toast
import tachiyomi.core.util.lang.withNonCancellableContext
import tachiyomi.core.util.lang.withUIContext

class CrashLogUtil(private val context: Context) {

    suspend fun dumpLogs() = withNonCancellableContext {
        try {
            val file = context.createFileInCacheDir("tachiyomi_crash_logs.txt")
            Runtime.getRuntime().exec("logcat *:E -d -f ${file.absolutePath}").waitFor()
            file.appendText(getDebugInfo())

            val uri = file.getUriCompat(context)
            context.startActivity(uri.toShareIntent(context, "text/plain"))
        } catch (e: Throwable) {
            withUIContext { context.toast("Failed to get logs") }
        }
    }

    fun getDebugInfo(): String {
        return """
            App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.FLAVOR}, ${BuildConfig.COMMIT_SHA}, ${BuildConfig.VERSION_CODE}, ${BuildConfig.BUILD_TIME})
            Android version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})
            Android build ID: ${Build.DISPLAY}
            Device brand: ${Build.BRAND}
            Device manufacturer: ${Build.MANUFACTURER}
            Device name: ${Build.DEVICE}
            Device model: ${Build.MODEL}
            Device product name: ${Build.PRODUCT}
        """.trimIndent()
    }
}
