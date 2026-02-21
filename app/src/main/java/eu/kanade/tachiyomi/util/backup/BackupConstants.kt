package eu.kanade.tachiyomi.util.backup

import java.io.File

/**
 * Constants and utilities for backup file handling.
 */
object BackupConstants {

    /**
     * Supported backup file extensions
     */
    val BACKUP_EXTENSIONS = listOf(".tachibk", ".proto.gz")

    /**
     * Maximum backup file size in bytes (500 MB)
     */
    const val MAX_BACKUP_SIZE = 500 * 1024 * 1024L

    /**
     * Minimum cache space buffer to keep after copying backup (50 MB)
     * This ensures the device has enough space for normal operations
     */
    const val MIN_CACHE_SPACE_BUFFER = 50 * 1024 * 1024L

    /**
     * Temp backup file name prefix
     */
    const val TEMP_BACKUP_PREFIX = "temp_backup_"

    /**
     * Time to keep temporary backup files in cache (1 hour in milliseconds)
     */
    const val TEMP_BACKUP_RETENTION_MS = 60 * 60 * 1000L

    /**
     * Date format pattern for displaying file modification dates
     */
    const val DATE_FORMAT_PATTERN = "dd/MM/yyyy HH:mm"

    /**
     * Checks if a filename represents a valid backup file based on extension.
     *
     * @param filename The filename to check
     * @return true if the filename ends with a valid backup extension
     */
    fun isBackupFile(filename: String): Boolean {
        return BACKUP_EXTENSIONS.any { filename.endsWith(it, ignoreCase = true) }
    }

    /**
     * Checks if a File is a valid backup file (exists, is file, has valid extension, within size limit).
     *
     * @param file The file to validate
     * @return true if the file is a valid backup file
     */
    fun isValidBackupFile(file: File): Boolean {
        return file.exists() &&
               file.isFile &&
               isBackupFile(file.name) &&
               file.length() in 1..MAX_BACKUP_SIZE
    }
}
