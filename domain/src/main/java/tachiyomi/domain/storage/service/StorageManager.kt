package tachiyomi.domain.storage.service

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.util.storage.DiskUtil
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
import java.util.UUID

class StorageManager(
    private val context: Context,
    storagePreferences: StoragePreferences,
) {

    private val scope = CoroutineScope(Dispatchers.IO)

    private var baseDir: UniFile? = getBaseDir(storagePreferences.baseStorageDirectory.get())

    private val _changes: Channel<Unit> = Channel(Channel.UNLIMITED)
    val changes = _changes.receiveAsFlow()
        .shareIn(scope, SharingStarted.Lazily, 1)

    init {
        storagePreferences.baseStorageDirectory.changes()
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

    private fun getBaseDir(uri: String): UniFile? {
        return UniFile.fromUri(context, uri.toUri())
            .takeIf { it?.exists() == true }
    }

    fun canWriteTo(uri: Uri): Boolean {
        val baseDir = UniFile.fromUri(context, uri)
            ?.takeIf { it.exists() && it.isDirectory }
            ?: return false

        var testDir: UniFile? = null
        var cleanupSuccessful = false

        return try {
            testDir = baseDir.createDirectory("$STORAGE_TEST_DIR_PREFIX${UUID.randomUUID()}") ?: return false
            val childDir = testDir.createDirectory(STORAGE_TEST_CHILD_DIR) ?: return false
            val testFile = childDir.createFile(STORAGE_TEST_FILE) ?: return false

            testFile.openOutputStream().use { output ->
                output.write(STORAGE_TEST_CONTENT)
            }

            cleanupSuccessful = testFile.delete() && childDir.delete() && testDir.delete()
            cleanupSuccessful
        } catch (_: Throwable) {
            false
        } finally {
            if (!cleanupSuccessful) {
                testDir?.takeIf { it.exists() }?.delete()
            }
        }
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
private const val STORAGE_TEST_DIR_PREFIX = "mihon_storage_test_"
private const val STORAGE_TEST_CHILD_DIR = "folder"
private const val STORAGE_TEST_FILE = "file.tmp"
private val STORAGE_TEST_CONTENT = byteArrayOf(0)
