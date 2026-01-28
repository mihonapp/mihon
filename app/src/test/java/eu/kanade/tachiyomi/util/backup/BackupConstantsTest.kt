package eu.kanade.tachiyomi.util.backup

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import java.io.File

@Execution(ExecutionMode.CONCURRENT)
class BackupConstantsTest {

    @Test
    fun `isBackupFile returns true for tachibk extension`() {
        BackupConstants.isBackupFile("backup.tachibk") shouldBe true
        BackupConstants.isBackupFile("my_backup.tachibk") shouldBe true
        BackupConstants.isBackupFile("/path/to/backup.tachibk") shouldBe true
    }

    @Test
    fun `isBackupFile returns true for proto gz extension`() {
        BackupConstants.isBackupFile("backup.proto.gz") shouldBe true
        BackupConstants.isBackupFile("my_backup.proto.gz") shouldBe true
        BackupConstants.isBackupFile("/path/to/backup.proto.gz") shouldBe true
    }

    @Test
    fun `isBackupFile is case insensitive`() {
        BackupConstants.isBackupFile("backup.TACHIBK") shouldBe true
        BackupConstants.isBackupFile("backup.TaChIbK") shouldBe true
        BackupConstants.isBackupFile("backup.PROTO.GZ") shouldBe true
        BackupConstants.isBackupFile("backup.Proto.Gz") shouldBe true
    }

    @Test
    fun `isBackupFile returns false for invalid extensions`() {
        BackupConstants.isBackupFile("backup.txt") shouldBe false
        BackupConstants.isBackupFile("backup.zip") shouldBe false
        BackupConstants.isBackupFile("backup.json") shouldBe false
        BackupConstants.isBackupFile("backup") shouldBe false
        BackupConstants.isBackupFile("") shouldBe false
    }

    @Test
    fun `isValidBackupFile returns false for non-existent file`(@TempDir tempDir: File) {
        val nonExistentFile = File(tempDir, "nonexistent.tachibk")
        BackupConstants.isValidBackupFile(nonExistentFile) shouldBe false
    }

    @Test
    fun `isValidBackupFile returns false for directory`(@TempDir tempDir: File) {
        val directory = File(tempDir, "backup_dir")
        directory.mkdir()
        BackupConstants.isValidBackupFile(directory) shouldBe false
    }

    @Test
    fun `isValidBackupFile returns false for invalid extension`(@TempDir tempDir: File) {
        val invalidFile = File(tempDir, "backup.txt")
        invalidFile.writeText("test content")
        BackupConstants.isValidBackupFile(invalidFile) shouldBe false
    }

    @Test
    fun `isValidBackupFile returns false for empty file`(@TempDir tempDir: File) {
        val emptyFile = File(tempDir, "backup.tachibk")
        emptyFile.createNewFile()
        BackupConstants.isValidBackupFile(emptyFile) shouldBe false
    }

    @Test
    fun `isValidBackupFile returns false for file exceeding max size`(@TempDir tempDir: File) {
        val largeFile = File(tempDir, "backup.tachibk")
        // Create a file larger than MAX_BACKUP_SIZE (use a small buffer to avoid memory issues)
        largeFile.outputStream().use { output ->
            val buffer = ByteArray(1024 * 1024) // 1MB buffer
            val iterations = (BackupConstants.MAX_BACKUP_SIZE / buffer.size).toInt() + 2
            repeat(iterations) {
                output.write(buffer)
            }
        }
        BackupConstants.isValidBackupFile(largeFile) shouldBe false
    }

    @Test
    fun `isValidBackupFile returns true for valid tachibk file`(@TempDir tempDir: File) {
        val validFile = File(tempDir, "backup.tachibk")
        validFile.writeText("valid backup content")
        BackupConstants.isValidBackupFile(validFile) shouldBe true
    }

    @Test
    fun `isValidBackupFile returns true for valid proto gz file`(@TempDir tempDir: File) {
        val validFile = File(tempDir, "backup.proto.gz")
        validFile.writeText("valid backup content")
        BackupConstants.isValidBackupFile(validFile) shouldBe true
    }

    @Test
    fun `constants have expected values`() {
        BackupConstants.MAX_BACKUP_SIZE shouldBe 500 * 1024 * 1024L
        BackupConstants.MIN_CACHE_SPACE_BUFFER shouldBe 50 * 1024 * 1024L
        BackupConstants.TEMP_BACKUP_PREFIX shouldBe "temp_backup_"
        BackupConstants.TEMP_BACKUP_RETENTION_MS shouldBe 60 * 60 * 1000L
        BackupConstants.DATE_FORMAT_PATTERN shouldBe "dd/MM/yyyy HH:mm"
        BackupConstants.BACKUP_EXTENSIONS shouldBe listOf(".tachibk", ".proto.gz")
    }
}
