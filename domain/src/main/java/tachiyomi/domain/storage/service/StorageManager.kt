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
            val createdTestDir = baseDir.createDirectory("$STORAGE_TEST_DIR_PREFIX${UUID.randomUUID()}") ?: return false
            testDir = createdTestDir

            val downloadsDir = createdTestDir.createDirectory(DOWNLOADS_PATH) ?: return false
            if (createdTestDir.createDirectory(DOWNLOADS_PATH) == null) return false

            val sourceDir = downloadsDir.createDirectory(STORAGE_TEST_SOURCE_DIR) ?: return false
            if (downloadsDir.createDirectory(STORAGE_TEST_SOURCE_DIR) == null) return false

            val mangaDir = sourceDir.createDirectory(STORAGE_TEST_MANGA_DIR) ?: return false
            if (sourceDir.createDirectory(STORAGE_TEST_MANGA_DIR) == null) return false

            var chapterDir = mangaDir.createDirectory(STORAGE_TEST_CHAPTER_DIR_TMP) ?: return false
            var pageFile = chapterDir.createFile(STORAGE_TEST_PAGE_FILE_TMP) ?: return false

            pageFile.openOutputStream().use { output ->
                output.write(STORAGE_TEST_CONTENT)
            }

            if (!pageFile.renameTo(STORAGE_TEST_PAGE_FILE)) return false
            pageFile = chapterDir.findFile(STORAGE_TEST_PAGE_FILE) ?: return false
            pageFile.openInputStream().use { input ->
                if (input.read() != STORAGE_TEST_CONTENT.first().toInt()) return false
            }
            if (chapterDir.listFiles().orEmpty().none { it.name == STORAGE_TEST_PAGE_FILE }) return false

            val comicInfoFile = chapterDir.createFile(STORAGE_TEST_COMIC_INFO_FILE) ?: return false
            comicInfoFile.openOutputStream().use { output ->
                output.write(STORAGE_TEST_COMIC_INFO_CONTENT)
            }

            val archiveFile = mangaDir.createFile(STORAGE_TEST_ARCHIVE_FILE_TMP) ?: return false
            archiveFile.openOutputStream().use { output ->
                output.write(STORAGE_TEST_CONTENT)
            }
            if (!archiveFile.renameTo(STORAGE_TEST_ARCHIVE_FILE)) return false
            if (mangaDir.findFile(STORAGE_TEST_ARCHIVE_FILE) == null) return false

            if (!chapterDir.renameTo(STORAGE_TEST_CHAPTER_DIR)) return false
            chapterDir = mangaDir.findFile(STORAGE_TEST_CHAPTER_DIR) ?: return false
            pageFile = chapterDir.findFile(STORAGE_TEST_PAGE_FILE) ?: return false
            if (chapterDir.findFile(STORAGE_TEST_COMIC_INFO_FILE) == null) return false
            if (chapterDir.listFiles().orEmpty().none { it.name == pageFile.name }) return false

            DiskUtil.createNoMediaFile(chapterDir, context)

            cleanupSuccessful = createdTestDir.deleteTree()
            cleanupSuccessful
        } catch (_: Throwable) {
            false
        } finally {
            if (!cleanupSuccessful) {
                testDir?.takeIf { it.exists() }?.deleteTree()
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
private const val STORAGE_TEST_SOURCE_DIR = "source"
private const val STORAGE_TEST_MANGA_DIR = "manga"
private const val STORAGE_TEST_CHAPTER_DIR_TMP = "chapter_tmp"
private const val STORAGE_TEST_CHAPTER_DIR = "chapter"
private const val STORAGE_TEST_PAGE_FILE_TMP = "001.tmp"
private const val STORAGE_TEST_PAGE_FILE = "001.jpg"
private const val STORAGE_TEST_COMIC_INFO_FILE = "ComicInfo.xml"
private const val STORAGE_TEST_ARCHIVE_FILE_TMP = "chapter.cbz_tmp"
private const val STORAGE_TEST_ARCHIVE_FILE = "chapter.cbz"
private val STORAGE_TEST_CONTENT = byteArrayOf(0)
private val STORAGE_TEST_COMIC_INFO_CONTENT = "<ComicInfo />".toByteArray()

private fun UniFile.deleteTree(): Boolean {
    var childrenDeleted = true
    try {
        listFiles().orEmpty().forEach {
            childrenDeleted = it.deleteTree() && childrenDeleted
        }
    } catch (_: Throwable) {
        childrenDeleted = false
    }

    val selfDeleted = try {
        delete()
    } catch (_: Throwable) {
        false
    }
    return childrenDeleted && selfDeleted
}
