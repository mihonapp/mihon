package eu.kanade.tachiyomi.data.backup

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class BackupLocationTest {

    @Test
    fun `volume-root backup is under the storage tree at autobackup filename`() {
        val location = backupLocation(
            uri = VOLUME_ROOT_BACKUP,
            storageTreeUri = STORAGE_TREE,
            primaryRoot = PRIMARY_ROOT,
        )

        location.relativePath shouldBe "autobackup/$BACKUP_FILENAME"
        location.fileName shouldBe BACKUP_FILENAME
        location.filePath shouldBe "$PRIMARY_ROOT/Documents/Mihon_test/autobackup/$BACKUP_FILENAME"
    }

    @Test
    fun `storage tree URI with a document suffix still locates the backup`() {
        val location = backupLocation(
            uri = VOLUME_ROOT_BACKUP,
            storageTreeUri = STORAGE_TREE_WITH_DOCUMENT,
            primaryRoot = PRIMARY_ROOT,
        )

        location.relativePath shouldBe "autobackup/$BACKUP_FILENAME"
        location.fileName shouldBe BACKUP_FILENAME
    }

    @Test
    fun `a different storage tree does not cover the backup`() {
        val location = backupLocation(
            uri = VOLUME_ROOT_BACKUP,
            storageTreeUri = OTHER_STORAGE_TREE,
            primaryRoot = PRIMARY_ROOT,
        )

        location.relativePath shouldBe null
        location.fileName shouldBe BACKUP_FILENAME
    }

    @Test
    fun `a Download backup is not under the Mihon storage tree`() {
        val location = backupLocation(
            uri = DOWNLOAD_BACKUP,
            storageTreeUri = STORAGE_TREE,
            primaryRoot = PRIMARY_ROOT,
        )

        location.relativePath shouldBe null
        location.fileName shouldBe "backup.tachibk"
        location.filePath shouldBe "$PRIMARY_ROOT/Download/backup.tachibk"
    }

    @Test
    fun `a MediaStore pick has no ExternalStorage location`() {
        val location = backupLocation(
            uri = MEDIA_STORE_BACKUP,
            storageTreeUri = STORAGE_TREE,
            primaryRoot = PRIMARY_ROOT,
        )

        location.relativePath shouldBe null
        location.fileName shouldBe null
        location.filePath shouldBe null
    }
}

private const val BACKUP_FILENAME = "app.mihon.tachibk"
private const val PRIMARY_ROOT = "/storage/emulated/0"
private const val VOLUME_ROOT_BACKUP =
    "content://com.android.externalstorage.documents/document/primary%3ADocuments%2FMihon_test%2Fautobackup%2F$BACKUP_FILENAME"
private const val STORAGE_TREE =
    "content://com.android.externalstorage.documents/tree/primary%3ADocuments%2FMihon_test"
private const val STORAGE_TREE_WITH_DOCUMENT =
    "$STORAGE_TREE/document/primary%3ADocuments%2FMihon_test"
private const val OTHER_STORAGE_TREE =
    "content://com.android.externalstorage.documents/tree/primary%3ADocuments%2FMihon"
private const val DOWNLOAD_BACKUP =
    "content://com.android.externalstorage.documents/document/primary%3ADownload%2Fbackup.tachibk"
private const val MEDIA_STORE_BACKUP = "content://media/external/file/42"
