package tachiyomi.core.common.storage

import android.content.Context
import android.os.Environment
import androidx.core.net.toUri
import eu.kanade.tachiyomi.util.system.DeviceUtil
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import java.io.File

class AndroidStorageFolderProvider(
    private val context: Context,
) : FolderProvider {

    override fun directory(): File {
        // Secure environment compatibility (isolated user profiles, work profiles)
        // In secure environments, the default external storage is not accessible.
        // We need to use app-specific storage which respects the isolation.
        val isSecureFolder = DeviceUtil.isInSecureFolder(context)

        if (isSecureFolder) {
            // Use app-specific external storage directory
            // This is accessible within Secure Folder and doesn't require storage permissions
            val appSpecificDir = context.getExternalFilesDir(null)

            if (appSpecificDir != null) {
                val targetDir = File(appSpecificDir, context.stringResource(MR.strings.app_name))

                // Create the directory if it doesn't exist
                if (!targetDir.exists()) {
                    val created = targetDir.mkdirs()
                    if (!created && !targetDir.exists()) {
                        logcat(LogPriority.ERROR) {
                            "Failed to create secure environment storage: ${targetDir.absolutePath}"
                        }
                        // Fall through to try default storage
                    } else {
                        logcat(LogPriority.INFO) { "Using secure environment storage: ${targetDir.absolutePath}" }
                        return targetDir
                    }
                } else {
                    // Directory already exists, verify it's usable
                    if (targetDir.isDirectory && targetDir.canWrite()) {
                        logcat(LogPriority.INFO) { "Using secure environment storage: ${targetDir.absolutePath}" }
                        return targetDir
                    } else {
                        logcat(LogPriority.ERROR) {
                            "Secure environment storage not writable: ${targetDir.absolutePath}"
                        }
                        // Fall through to try default storage
                    }
                }
            } else {
                logcat(LogPriority.ERROR) { "App-specific storage not available in secure environment" }
            }
        }

        // Default behavior for non-Secure Folder environments
        val externalStorageDir = Environment.getExternalStorageDirectory()
        val defaultDir = File(
            externalStorageDir.absolutePath + File.separator +
                context.stringResource(MR.strings.app_name),
        )

        // Validate that the directory is accessible
        val isAccessible = isDirectoryAccessible(defaultDir)

        if (!isAccessible) {
            logcat(LogPriority.WARN) { "Default storage not accessible, using app-specific fallback" }

            // Fallback to app-specific storage if default is not accessible
            val fallbackDir = context.getExternalFilesDir(null)
            if (fallbackDir != null) {
                val targetDir = File(fallbackDir, context.stringResource(MR.strings.app_name))

                if (!targetDir.exists()) {
                    targetDir.mkdirs()
                }

                if (targetDir.exists() && targetDir.canWrite()) {
                    logcat(LogPriority.INFO) { "Using app-specific storage: ${targetDir.absolutePath}" }
                    return targetDir
                } else {
                    logcat(LogPriority.ERROR) { "Fallback storage not usable: ${targetDir.absolutePath}" }
                }
            } else {
                logcat(LogPriority.ERROR) { "Fallback storage unavailable" }
            }
        }

        logcat(LogPriority.INFO) { "Using default storage: ${defaultDir.absolutePath}" }
        return defaultDir
    }

    override fun path(): String {
        return directory().toUri().toString()
    }

    /**
     * Checks if a directory is accessible (can be created or already exists with write access).
     *
     * This method is defensive and handles edge cases:
     * - Directory exists but is actually a file
     * - Directory exists but is not writable
     * - Parent directory doesn't exist or is not writable
     * - SecurityException when accessing directory
     * - IOException when creating directory
     *
     * @param directory The directory to check
     * @return true if the directory exists and is writable, or can be created successfully
     */
    private fun isDirectoryAccessible(directory: File): Boolean {
        return try {
            when {
                // Case 1: Directory exists
                directory.exists() -> {
                    directory.isDirectory && directory.canWrite()
                }
                // Case 2: Directory doesn't exist, try to create it
                else -> {
                    // Attempt to create the directory
                    val created = directory.mkdirs()
                    // Verify the directory is now accessible
                    created || (directory.exists() && directory.isDirectory && directory.canWrite())
                }
            }
        } catch (e: SecurityException) {
            logcat(LogPriority.ERROR, e) { "SecurityException accessing: ${directory.absolutePath}" }
            false
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Error accessing: ${directory.absolutePath}" }
            false
        }
    }
}
