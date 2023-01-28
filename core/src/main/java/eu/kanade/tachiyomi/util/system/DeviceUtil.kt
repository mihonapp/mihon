package eu.kanade.tachiyomi.util.system

import android.annotation.SuppressLint
import android.os.Build
import logcat.LogPriority
import tachiyomi.core.util.system.logcat

object DeviceUtil {

    val isMiui by lazy {
        getSystemProperty("ro.miui.ui.version.name")?.isNotEmpty() ?: false
    }

    /**
     * Extracts the MIUI major version code from a string like "V12.5.3.0.QFGMIXM".
     *
     * @return MIUI major version code (e.g., 13) or -1 if can't be parsed.
     */
    val miuiMajorVersion by lazy {
        if (!isMiui) return@lazy -1

        Build.VERSION.INCREMENTAL
            .substringBefore('.')
            .trimStart('V')
            .toIntOrNull() ?: -1
    }

    @SuppressLint("PrivateApi")
    fun isMiuiOptimizationDisabled(): Boolean {
        val sysProp = getSystemProperty("persist.sys.miui_optimization")
        if (sysProp == "0" || sysProp == "false") {
            return true
        }

        return try {
            Class.forName("android.miui.AppOpsUtils")
                .getDeclaredMethod("isXOptMode")
                .invoke(null) as Boolean
        } catch (e: Exception) {
            false
        }
    }

    val isSamsung by lazy {
        Build.MANUFACTURER.equals("samsung", ignoreCase = true)
    }

    val invalidDefaultBrowsers = listOf(
        "android",
        "com.huawei.android.internal.app",
        "com.zui.resolver",
    )

    @SuppressLint("PrivateApi")
    private fun getSystemProperty(key: String?): String? {
        return try {
            Class.forName("android.os.SystemProperties")
                .getDeclaredMethod("get", String::class.java)
                .invoke(null, key) as String
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Unable to use SystemProperties.get()" }
            null
        }
    }
}
