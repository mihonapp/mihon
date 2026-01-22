package eu.kanade.tachiyomi.util.backup

import android.content.Context
import android.os.StatFs
import eu.kanade.tachiyomi.util.system.safeCanonicalFile
import kotlinx.coroutines.DelicateCoroutinesApi
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

object BackupUtil {

    private val copyCounter = AtomicInteger(0)

    /**
     * Copies a backup file to app cache directory to avoid permission issues in Secure Folder.
     *
     * @param sourcePath Path to the backup file
     * @param context Application context
     * @return Result containing the absolute path of the cached file, or the exception if failed
     */
    fun copyBackupToCache(sourcePath: String, context: Context): Result<String> {
        return try {
            val sourceFile = File(sourcePath).safeCanonicalFile()
                ?: return Result.failure(IllegalArgumentException("Invalid file path"))

            // Comprehensive validation using BackupConstants
            if (!BackupConstants.isValidBackupFile(sourceFile)) {
                return when {
                    !sourceFile.exists() -> Result.failure(java.io.FileNotFoundException("Source file does not exist"))
                    !sourceFile.isFile -> Result.failure(IllegalArgumentException("Source must be a file"))
                    !BackupConstants.isBackupFile(sourceFile.name) -> Result.failure(IllegalArgumentException("Invalid backup file format"))
                    sourceFile.length() > BackupConstants.MAX_BACKUP_SIZE -> {
                        Result.failure(IllegalArgumentException("Backup file too large (max ${BackupConstants.MAX_BACKUP_SIZE / 1024 / 1024}MB)"))
                    }
                    else -> Result.failure(IllegalArgumentException("Invalid backup file"))
                }
            }

            // Check available disk space before copying
            val requiredSpace = sourceFile.length() + BackupConstants.MIN_CACHE_SPACE_BUFFER
            val availableSpace = getAvailableCacheSpace(context)
            if (availableSpace < requiredSpace) {
                // Try to free up space by cleaning old backups
                cleanOldTempBackupsSync(context)

                // Check again after cleanup
                val availableAfterCleanup = getAvailableCacheSpace(context)
                if (availableAfterCleanup < requiredSpace) {
                    return Result.failure(
                        IllegalStateException(
                            "Insufficient disk space. Required: ${requiredSpace / 1024 / 1024}MB, " +
                            "Available: ${availableAfterCleanup / 1024 / 1024}MB"
                        )
                    )
                }
            }

            // Clean old temp backups every 10 copies (async optimization)
            if (copyCounter.incrementAndGet() % 10 == 0) {
                cleanOldTempBackupsAsync(context)
            }

            // Copy to cache directory with unique name using UUID instead of deprecated thread ID
            val cacheFile = File(
                context.cacheDir,
                "${BackupConstants.TEMP_BACKUP_PREFIX}${System.currentTimeMillis()}_${UUID.randomUUID()}.tachibk"
            )
            sourceFile.inputStream().use { input ->
                cacheFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            logcat(LogPriority.INFO) { "Backup file copied to cache: ${cacheFile.absolutePath}" }
            Result.success(cacheFile.absolutePath)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Error copying backup file: ${e.message}" }
            Result.failure(e)
        }
    }

    /**
     * Gets the available space in the cache directory.
     *
     * @param context Application context
     * @return Available space in bytes
     */
    private fun getAvailableCacheSpace(context: Context): Long {
        return try {
            val stat = StatFs(context.cacheDir.path)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Failed to get cache space" }
            0L
        }
    }

    /**
     * Cleans old temporary backup files from cache directory synchronously.
     * Used when we need to free up space before copying.
     *
     * @param context Application context
     * @return Number of files deleted
     */
    private fun cleanOldTempBackupsSync(context: Context): Int {
        return try {
            val cutoffTime = System.currentTimeMillis() - BackupConstants.TEMP_BACKUP_RETENTION_MS
            val files = context.cacheDir.listFiles()
                ?.filter { it.name.startsWith(BackupConstants.TEMP_BACKUP_PREFIX) && it.lastModified() < cutoffTime }
                ?: emptyList()

            var deletedCount = 0
            files.forEach { file ->
                if (file.delete()) {
                    deletedCount++
                }
            }

            if (deletedCount > 0) {
                logcat(LogPriority.INFO) { "Cleaned $deletedCount old temp backup files" }
            }
            deletedCount
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Failed to clean old temp backups" }
            0
        }
    }

    /**
     * Cleans old temporary backup files from cache directory asynchronously.
     * Keeps files created in the last hour, removes older ones.
     * Uses coroutines instead of raw threads for better resource management.
     */
    @OptIn(DelicateCoroutinesApi::class)
    private fun cleanOldTempBackupsAsync(context: Context) {
        launchIO {
            try {
                val deletedCount = cleanOldTempBackupsSync(context)
                if (deletedCount > 0) {
                    logcat(LogPriority.DEBUG) { "Async cleanup completed: $deletedCount files removed" }
                }
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Async cleanup failed" }
            }
        }
    }
}
