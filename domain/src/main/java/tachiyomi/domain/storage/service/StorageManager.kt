package tachiyomi.domain.storage.service

import android.content.Context
import androidx.core.net.toUri
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.system.DeviceUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

class StorageManager(
    private val context: Context,
    private val storagePreferences: StoragePreferences,
) {

    private val scope = CoroutineScope(Dispatchers.IO)

    private var baseDir: UniFile? = initializeBaseDir()

    private val _changes: Channel<Unit> = Channel(Channel.UNLIMITED)
    val changes = _changes.receiveAsFlow()
        .shareIn(scope, SharingStarted.Lazily, 1)

    init {
        storagePreferences.baseStorageDirectory().changes()
            .drop(1)
            .distinctUntilChanged()
            .onEach { uri ->
                baseDir = getBaseDir(uri)
                baseDir?.let { parent ->
                    parent.createDirectory(AUTOMATIC_BACKUPS_PATH)
                    parent.createDirectory(LOCAL_SOURCE_PATH)
                    parent.createDirectory(DOWNLOADS_PATH).also {
                        DiskUtil.createNoMediaFile(it, context)
                    }
                }
                _changes.send(Unit)
            }
            .launchIn(scope)
    }

    private fun initializeBaseDir(): UniFile? {
        val currentUri = storagePreferences.baseStorageDirectory().get()

        // Check if we're in a secure environment
        if (DeviceUtil.isInSecureFolder(context)) {
            logcat(LogPriority.INFO) { "StorageManager: Running in Secure Folder, current URI: $currentUri" }

            // In Secure Folder, URIs pointing to 'primary:' storage are invalid
            // because they point to the main profile's storage which is not accessible
            if (isInvalidUriForSecureFolder(currentUri)) {
                logcat(LogPriority.WARN) {
                    "StorageManager: Current URI points to primary storage which is inaccessible in Secure Folder"
                }

                // Force reset to default (app-specific storage)
                val defaultPath = storagePreferences.baseStorageDirectory().defaultValue()
                logcat(LogPriority.INFO) { "StorageManager: Force resetting to default path: $defaultPath" }

                storagePreferences.baseStorageDirectory().set(defaultPath)
                return getBaseDir(defaultPath)
            }

            val currentBaseDir = getBaseDir(currentUri)

            // If current storage is null or inaccessible, force reset to default
            if (currentBaseDir == null) {
                logcat(LogPriority.WARN) {
                    "StorageManager: Current storage is inaccessible in Secure Folder, resetting to default"
                }

                val defaultPath = storagePreferences.baseStorageDirectory().defaultValue()
                logcat(LogPriority.INFO) { "StorageManager: Resetting to default path: $defaultPath" }

                storagePreferences.baseStorageDirectory().set(defaultPath)
                return getBaseDir(defaultPath)
            }

            return currentBaseDir
        }

        return getBaseDir(currentUri)
    }

    /**
     * Checks if a URI is invalid for use in secure environments.
     *
     * URIs containing "primary:" or "0@" point to the main user profile's storage,
     * which is not accessible from within isolated secure environments.
     *
     * @param uri The URI string to check
     * @return true if the URI is invalid for secure environments, false otherwise
     */
    internal fun isInvalidUriForSecureFolder(uri: String): Boolean {
        return uri.contains(PRIMARY_STORAGE_PREFIX, ignoreCase = true) ||
               uri.contains(PRIMARY_STORAGE_USER_ID, ignoreCase = false)
    }

    private fun getBaseDir(uri: String): UniFile? {
        val uniFile = UniFile.fromUri(context, uri.toUri())
        val exists = uniFile?.exists() == true

        logcat(LogPriority.INFO) { "StorageManager: Checking URI: $uri - Exists: $exists" }

        return uniFile.takeIf { exists }
    }

    fun getAutomaticBackupsDirectory(): UniFile? {
        return baseDir?.createDirectory(AUTOMATIC_BACKUPS_PATH)
    }

    fun getDownloadsDirectory(): UniFile? {
        return baseDir?.createDirectory(DOWNLOADS_PATH)
    }

    fun getLocalSourceDirectory(): UniFile? {
        return baseDir?.createDirectory(LOCAL_SOURCE_PATH)
    }
}

private const val AUTOMATIC_BACKUPS_PATH = "autobackup"
private const val DOWNLOADS_PATH = "downloads"
private const val LOCAL_SOURCE_PATH = "local"

/**
 * URI prefix for primary storage that is inaccessible in Secure Folder
 */
private const val PRIMARY_STORAGE_PREFIX = "primary:"

/**
 * User ID marker for primary storage that is inaccessible in Secure Folder
 */
private const val PRIMARY_STORAGE_USER_ID = "0@"
