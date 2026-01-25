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

        logcat(LogPriority.INFO) {
            "Storage initialization - Secure environment: $isSecureFolder, " +
            "Package: ${context.packageName}, " +
            "User: ${android.os.Process.myUserHandle().hashCode()}"
        }

        if (isSecureFolder) {
            logcat(LogPriority.INFO) { "Detected secure environment - using app-specific storage" }

            // Use app-specific external storage directory
            // This is accessible within Secure Folder and doesn't require storage permissions
            val appSpecificDir = context.getExternalFilesDir(null)

            if (appSpecificDir != null) {
                logcat(LogPriority.DEBUG) {
                    "App-specific directory available: ${appSpecificDir.absolutePath}, " +
                    "Exists: ${appSpecificDir.exists()}, " +
                    "CanRead: ${appSpecificDir.canRead()}, " +
                    "CanWrite: ${appSpecificDir.canWrite()}"
                }

                val targetDir = File(appSpecificDir, context.stringResource(MR.strings.app_name))

                // Create the directory if it doesn't exist
                if (!targetDir.exists()) {
                    logcat(LogPriority.INFO) { "Creating Secure Folder storage directory: ${targetDir.absolutePath}" }
                    val created = targetDir.mkdirs()

                    if (!created && !targetDir.exists()) {
                        logcat(LogPriority.ERROR) {
                            "Failed to create Secure Folder storage directory. " +
                            "Parent exists: ${targetDir.parentFile?.exists()}, " +
                            "Parent writable: ${targetDir.parentFile?.canWrite()}"
                        }
                        // Fall through to try default storage
                    } else {
                        logcat(LogPriority.INFO) {
                            "Directory created successfully: ${targetDir.absolutePath}, " +
                            "CanWrite: ${targetDir.canWrite()}"
                        }
                        return targetDir
                    }
                } else {
                    // Directory already exists, verify it's usable
                    if (targetDir.isDirectory && targetDir.canWrite()) {
                        logcat(LogPriority.INFO) { "Using existing Secure Folder storage: ${targetDir.absolutePath}" }
                        return targetDir
                    } else {
                        logcat(LogPriority.ERROR) {
                            "Secure Folder storage exists but is not usable. " +
                            "IsDirectory: ${targetDir.isDirectory}, " +
                            "CanWrite: ${targetDir.canWrite()}"
                        }
                        // Fall through to try default storage
                    }
                }
            } else {
                logcat(LogPriority.ERROR) {
                    "App-specific storage not available in Secure Folder! " +
                    "Context.getExternalFilesDir(null) returned null"
                }
            }
        }

        // Default behavior for non-Secure Folder environments
        val externalStorageDir = Environment.getExternalStorageDirectory()

        logcat(LogPriority.DEBUG) {
            "External storage state: ${Environment.getExternalStorageState()}, " +
            "Path: ${externalStorageDir.absolutePath}, " +
            "Exists: ${externalStorageDir.exists()}, " +
            "CanWrite: ${externalStorageDir.canWrite()}"
        }

        val defaultDir = File(
            externalStorageDir.absolutePath + File.separator +
                context.stringResource(MR.strings.app_name),
        )

        logcat(LogPriority.INFO) { "Checking default storage: ${defaultDir.absolutePath}" }

        // Validate that the directory is accessible
        val isAccessible = isDirectoryAccessible(defaultDir)
        logcat(LogPriority.DEBUG) {
            "Default directory accessibility check: $isAccessible, " +
            "Exists: ${defaultDir.exists()}, " +
            "IsDirectory: ${defaultDir.isDirectory}, " +
            "CanWrite: ${defaultDir.canWrite()}"
        }

        if (!isAccessible) {
            logcat(LogPriority.WARN) {
                "Default storage directory not accessible: ${defaultDir.absolutePath}, " +
                "Attempting fallback to app-specific storage"
            }

            // Fallback to app-specific storage if default is not accessible
            val fallbackDir = context.getExternalFilesDir(null)
            if (fallbackDir != null) {
                val targetDir = File(fallbackDir, context.stringResource(MR.strings.app_name))

                if (!targetDir.exists()) {
                    val created = targetDir.mkdirs()
                    logcat(LogPriority.INFO) {
                        "Creating fallback directory: ${targetDir.absolutePath}, " +
                        "Success: $created"
                    }
                }

                if (targetDir.exists() && targetDir.canWrite()) {
                    logcat(LogPriority.INFO) { "Using app-specific storage as fallback: ${targetDir.absolutePath}" }
                    return targetDir
                } else {
                    logcat(LogPriority.ERROR) {
                        "Fallback directory not usable: ${targetDir.absolutePath}, " +
                        "Exists: ${targetDir.exists()}, " +
                        "CanWrite: ${targetDir.canWrite()}"
                    }
                }
            } else {
                logcat(LogPriority.ERROR) { "Fallback app-specific storage also unavailable!" }
            }
        }

        logcat(LogPriority.INFO) {
            "Using default storage: ${defaultDir.absolutePath}, " +
            "Exists: ${defaultDir.exists()}, " +
            "CanWrite: ${defaultDir.canWrite()}"
        }
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
                    if (!directory.isDirectory) {
                        logcat(LogPriority.ERROR) {
                            "Path exists but is not a directory: ${directory.absolutePath}"
                        }
                        false
                    } else {
                        val canWrite = directory.canWrite()
                        if (!canWrite) {
                            logcat(LogPriority.WARN) {
                                "Directory exists but is not writable: ${directory.absolutePath}"
                            }
                        }
                        canWrite
                    }
                }
                // Case 2: Directory doesn't exist, try to create it
                else -> {
                    // Check parent directory accessibility first
                    val parent = directory.parentFile
                    if (parent != null && !parent.exists()) {
                        logcat(LogPriority.DEBUG) {
                            "Parent directory doesn't exist, will be created: ${parent.absolutePath}"
                        }
                    }

                    // Attempt to create the directory
                    val created = directory.mkdirs()

                    if (!created && !directory.exists()) {
                        logcat(LogPriority.WARN) {
                            "Failed to create directory: ${directory.absolutePath}, " +
                            "Parent exists: ${parent?.exists()}, " +
                            "Parent writable: ${parent?.canWrite()}"
                        }
                        false
                    } else {
                        // Verify the directory is now accessible
                        val accessible = directory.exists() && directory.isDirectory && directory.canWrite()
                        if (!accessible) {
                            logcat(LogPriority.WARN) {
                                "Directory created but not fully accessible: ${directory.absolutePath}, " +
                                "Exists: ${directory.exists()}, " +
                                "IsDirectory: ${directory.isDirectory}, " +
                                "CanWrite: ${directory.canWrite()}"
                            }
                        }
                        accessible
                    }
                }
            }
        } catch (e: SecurityException) {
            logcat(LogPriority.ERROR, e) {
                "SecurityException checking directory accessibility: ${directory.absolutePath}"
            }
            false
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) {
                "Unexpected error checking directory accessibility: ${directory.absolutePath}"
            }
            false
        }
    }
}
