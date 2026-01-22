package eu.kanade.tachiyomi.util.system

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.os.UserManager
import androidx.core.content.getSystemService
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

object DeviceUtil {

    /**
     * Regex pattern to extract storage emulated path (e.g., "/storage/emulated/150")
     */
    private val STORAGE_EMULATED_PATTERN = Regex("""(/storage/emulated/\d+)/""")

    /**
     * Minimum user ID for Samsung Secure Folder.
     * Secure Folder typically uses user IDs >= 150.
     */
    private const val SECURE_FOLDER_MIN_USER_ID = 150

    /**
     * Regex pattern to detect Secure Folder user ID in data directory path.
     * Matches patterns like "/data/user/150/", "/data/user/151/", etc.
     */
    private val SECURE_FOLDER_DATA_PATH_PATTERN = Regex(".*/user/1[5-9]\\d+/.*")

    val isMiui: Boolean by lazy {
        getSystemProperty("ro.miui.ui.version.name")?.isNotEmpty() ?: false
    }

    /**
     * Extracts the MIUI major version code from a string like "V12.5.3.0.QFGMIXM".
     *
     * @return MIUI major version code (e.g., 13) or null if can't be parsed.
     */
    val miuiMajorVersion: Int? by lazy {
        if (!isMiui) return@lazy null

        Build.VERSION.INCREMENTAL
            .substringBefore('.')
            .trimStart('V')
            .toIntOrNull()
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

    val isSamsung: Boolean by lazy {
        Build.MANUFACTURER.equals("samsung", ignoreCase = true)
    }

    val oneUiVersion: Double? by lazy {
        try {
            val semPlatformIntField = Build.VERSION::class.java.getDeclaredField("SEM_PLATFORM_INT")
            val version = semPlatformIntField.getInt(null) - 90000
            if (version < 0) {
                1.0
            } else {
                ((version / 10000).toString() + "." + version % 10000 / 100).toDouble()
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get the base storage path for Secure Folder.
     * Returns the root storage path (e.g., "/storage/emulated/150/")
     *
     * @param context Application context
     * @return Base storage path with trailing slash
     */
    fun getSecureFolderBasePath(context: Context): String {
        val externalStorageDir = android.os.Environment.getExternalStorageDirectory()
        val externalFilesDir = context.getExternalFilesDir(null)

        val basePathStr = when {
            externalStorageDir?.exists() == true -> externalStorageDir.absolutePath
            externalFilesDir != null -> {
                val path = externalFilesDir.absolutePath
                val match = STORAGE_EMULATED_PATTERN.find(path)
                match?.groupValues?.get(1) ?: "/storage/emulated/0"
            }
            else -> "/storage/emulated/0"
        }

        return if (basePathStr.endsWith("/")) basePathStr else "$basePathStr/"
    }

    /**
     * Detects if the app is running inside Samsung Knox Secure Folder.
     *
     * Secure Folder creates an isolated environment using Android's managed profile feature.
     * This is important for storage access as the default external storage path may not
     * be accessible from within the secure folder on S21+ devices.
     */
    fun isInSecureFolder(context: Context): Boolean {
        if (!isSamsung) {
            logcat(LogPriority.DEBUG) { "Not a Samsung device" }
            return false
        }

        return try {
            val userManager = context.getSystemService<UserManager>()

            // Check if we're running in a managed profile (Secure Folder uses this)
            val isManagedProfile = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                userManager?.isManagedProfile ?: false
            } else {
                false
            }

            // Additional check: Secure Folder typically uses user ID >= 150
            val userId = Process.myUserHandle().hashCode()
            val isSecureFolderUserId = userId >= SECURE_FOLDER_MIN_USER_ID

            // Knox Secure Folder detection via system properties
            val knoxVersion = getSystemProperty("ro.build.characteristics")
            val hasKnox = knoxVersion?.contains("knox", ignoreCase = true) == true

            // Check if the data directory path contains "knox" or user ID pattern
            val dataDir = context.dataDir?.absolutePath ?: ""
            val hasKnoxInPath = dataDir.contains("knox", ignoreCase = true) ||
                                dataDir.matches(SECURE_FOLDER_DATA_PATH_PATTERN)

            val result = isManagedProfile || hasKnoxInPath || (isSamsung && isSecureFolderUserId && hasKnox)

            logcat(LogPriority.INFO) {
                """Secure Folder detection:
                  - Managed Profile: $isManagedProfile
                  - User ID: $userId (Secure Folder range: $isSecureFolderUserId)
                  - Knox in system: $hasKnox
                  - Knox in path: $hasKnoxInPath
                  - Data dir: $dataDir
                  - Result: $result
                """.trimIndent()
            }

            result
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Unable to detect Secure Folder" }
            false
        }
    }

    /**
     * A list of package names that may be incorrectly resolved as usable browsers by
     * the system.
     *
     * If these are resolved for [android.content.Intent.ACTION_VIEW], it prevents the
     * system from opening a proper browser or any usable app .
     *
     * Some of them may only be present on certain manufacturer's devices.
     */
    val invalidDefaultBrowsers = listOf(
        "android",
        // Honor
        "com.hihonor.android.internal.app",
        // Huawei
        "com.huawei.android.internal.app",
        // Lenovo
        "com.zui.resolver",
        // Infinix
        "com.transsion.resolver",
        // Xiaomi Redmi
        "com.android.intentresolver",
    )

    /**
     * ActivityManager#isLowRamDevice is based on a system property, which isn't
     * necessarily trustworthy. 1GB is supposedly the regular threshold.
     *
     * Instead, we consider anything with less than 3GB of RAM as low memory
     * considering how heavy image processing can be.
     */
    fun isLowRamDevice(context: Context): Boolean {
        val memInfo = ActivityManager.MemoryInfo()
        context.getSystemService<ActivityManager>()!!.getMemoryInfo(memInfo)
        val totalMemBytes = memInfo.totalMem
        return totalMemBytes < 3L * 1024 * 1024 * 1024
    }

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
