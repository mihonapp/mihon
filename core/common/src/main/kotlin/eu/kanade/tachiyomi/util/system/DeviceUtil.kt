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
     * Minimum user ID for isolated secure environments.
     * Secure folders and work profiles typically use user IDs >= 150.
     */
    private const val SECURE_FOLDER_MIN_USER_ID = 150

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
     * Get the base storage path for secure environments.
     * Returns the root storage path (e.g., "/storage/emulated/150/")
     *
     * Uses multiple strategies to determine the base path:
     * 1. External storage directory (preferred, accessible in most cases)
     * 2. Extract from app-specific files directory (fallback for restricted environments)
     * 3. Default to "/storage/emulated/0/" (last resort)
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
     * Detects if the app is running inside an isolated secure environment.
     *
     * This includes:
     * - Samsung Knox Secure Folder
     * - Motorola Secure Folder
     * - Android Work Profile
     * - Other manufacturer-specific secure environments
     *
     * These environments use Android's managed profile feature or isolated user IDs,
     * which prevents SAF (Storage Access Framework) from working correctly.
     * Detection is necessary to use alternative storage access methods.
     */
    fun isInSecureFolder(context: Context): Boolean {
        return try {
            val userManager = context.getSystemService<UserManager>()

            // Primary check: managed profile (covers most secure environments)
            val isManagedProfile = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                userManager?.isManagedProfile ?: false
            } else {
                false
            }

            // Secondary check: Isolated user ID >= 150 (common pattern across manufacturers)
            val userId = Process.myUserHandle().hashCode()
            val isIsolatedUserId = userId >= SECURE_FOLDER_MIN_USER_ID

            // Result: true if either detection method succeeds
            val result = isManagedProfile || isIsolatedUserId

            logcat(LogPriority.INFO) {
                """Isolated environment detection:
                  - Manufacturer: ${Build.MANUFACTURER}
                  - Model: ${Build.MODEL}
                  - Managed Profile: $isManagedProfile
                  - User ID: $userId (Isolated range: >= $SECURE_FOLDER_MIN_USER_ID)
                  - Result: $result
                """.trimIndent()
            }

            result
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Unable to detect isolated environment" }
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
