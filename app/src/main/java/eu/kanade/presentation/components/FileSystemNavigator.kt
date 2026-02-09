package eu.kanade.presentation.components

import eu.kanade.tachiyomi.util.backup.BackupConstants
import java.io.File

/**
 * Navigator for file system operations with validation and filtering.
 * Separated from UI logic to enable unit testing.
 */
internal class FileSystemNavigator {

    /**
     * Lists files and folders in a directory based on the picker mode.
     *
     * @param path Directory path to list
     * @param mode Picker mode (FOLDER or FILE)
     * @return Result containing list of PickerItem or error
     */
    fun listDirectory(path: String, mode: PickerMode): Result<List<PickerItem>> {
        return try {
            val directory = File(path)

            if (!directory.exists()) {
                return Result.failure(IllegalArgumentException("Directory does not exist"))
            }

            if (!directory.isDirectory) {
                return Result.failure(IllegalArgumentException("Path is not a directory"))
            }

            val files = directory.listFiles() ?: return Result.success(emptyList())

            val items = when (mode) {
                PickerMode.FOLDER -> {
                    // Only list directories
                    files.filter { it.isDirectory }
                        .map { PickerItem(it, PickerItemType.FOLDER) }
                }
                PickerMode.FILE -> {
                    // List both directories and backup files
                    files.mapNotNull { file ->
                        when {
                            file.isDirectory -> PickerItem(file, PickerItemType.FOLDER)
                            BackupConstants.isBackupFile(file.name) -> PickerItem(file, PickerItemType.FILE)
                            else -> null
                        }
                    }
                }
            }

            // Sort: folders first, then files, both alphabetically
            val sorted = items.sortedWith(
                compareBy<PickerItem> { it.type != PickerItemType.FOLDER }
                    .thenBy { it.file.name.lowercase() }
            )

            Result.success(sorted)
        } catch (e: SecurityException) {
            Result.failure(SecurityException("Access denied to directory", e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Creates a new folder in the specified parent directory.
     *
     * @param parentPath Parent directory path
     * @param folderName Name of the new folder
     * @return Result containing the created File or error
     */
    fun createFolder(parentPath: String, folderName: String): Result<File> {
        return try {
            if (folderName.isBlank()) {
                return Result.failure(IllegalArgumentException("Folder name cannot be empty"))
            }

            if (folderName.contains(File.separator)) {
                return Result.failure(IllegalArgumentException("Folder name cannot contain path separators"))
            }

            val parent = File(parentPath)
            if (!parent.exists() || !parent.isDirectory) {
                return Result.failure(IllegalArgumentException("Parent directory does not exist"))
            }

            val newFolder = File(parent, folderName)

            if (newFolder.exists()) {
                return Result.failure(IllegalArgumentException("Folder already exists"))
            }

            val created = newFolder.mkdirs()
            if (!created) {
                return Result.failure(IllegalStateException("Failed to create folder"))
            }

            Result.success(newFolder)
        } catch (e: SecurityException) {
            Result.failure(SecurityException("Permission denied to create folder", e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Gets the parent directory path, or null if at root.
     *
     * @param currentPath Current directory path
     * @return Parent directory path or null
     */
    fun getParentPath(currentPath: String): String? {
        val file = File(currentPath)
        return file.parent
    }

    /**
     * Checks if the app has "All Files Access" permission on Android 11+.
     * This is needed to access certain directories outside the app's sandbox.
     *
     * @return true if permission is granted or not required
     */
    fun hasAllFilesPermission(): Boolean {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                android.os.Environment.isExternalStorageManager()
            } else {
                true // Not required on older Android versions
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Validates if a path is accessible and exists.
     *
     * @param path Path to validate
     * @return Result indicating success or error
     */
    fun validatePath(path: String): Result<Unit> {
        return try {
            val file = File(path)
            when {
                !file.exists() -> Result.failure(IllegalArgumentException("Path does not exist"))
                !file.canRead() -> Result.failure(SecurityException("Cannot read path"))
                else -> Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
