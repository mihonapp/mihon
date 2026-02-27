package eu.kanade.tachiyomi.util.backup

import android.content.Context
import android.os.StatFs
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import java.io.File

@Execution(ExecutionMode.CONCURRENT)
class BackupUtilTest {

    private lateinit var mockContext: Context
    private lateinit var tempCacheDir: File

    @BeforeEach
    fun setup(@TempDir tempDir: File) {
        tempCacheDir = File(tempDir, "cache").apply { mkdirs() }
        mockContext = mockk(relaxed = true) {
            every { cacheDir } returns tempCacheDir
        }

        // Mock StatFs for disk space checks
        mockkConstructor(StatFs::class)
        every { anyConstructed<StatFs>().availableBlocksLong } returns 1000L
        every { anyConstructed<StatFs>().blockSizeLong } returns 1024L * 1024L // 1MB blocks
    }

    @AfterEach
    fun teardown() {
        unmockkConstructor(StatFs::class)
    }

    @Test
    fun `copyBackupToCache returns failure for non-existent file`(@TempDir tempDir: File) {
        val nonExistentPath = File(tempDir, "nonexistent.tachibk").absolutePath

        val result = BackupUtil.copyBackupToCache(nonExistentPath, mockContext)

        result.isFailure shouldBe true
        result.exceptionOrNull().shouldBeInstanceOf<java.io.FileNotFoundException>()
    }

    @Test
    fun `copyBackupToCache returns failure for directory`(@TempDir tempDir: File) {
        val directory = File(tempDir, "backup_dir").apply { mkdirs() }

        val result = BackupUtil.copyBackupToCache(directory.absolutePath, mockContext)

        result.isFailure shouldBe true
        result.exceptionOrNull().shouldBeInstanceOf<IllegalArgumentException>()
        result.exceptionOrNull()?.message shouldContain "Source must be a file"
    }

    @Test
    fun `copyBackupToCache returns failure for invalid extension`(@TempDir tempDir: File) {
        val invalidFile = File(tempDir, "backup.txt").apply {
            writeText("test content")
        }

        val result = BackupUtil.copyBackupToCache(invalidFile.absolutePath, mockContext)

        result.isFailure shouldBe true
        result.exceptionOrNull().shouldBeInstanceOf<IllegalArgumentException>()
        result.exceptionOrNull()?.message shouldContain "Invalid backup file format"
    }

    @Test
    fun `copyBackupToCache returns failure for empty file`(@TempDir tempDir: File) {
        val emptyFile = File(tempDir, "backup.tachibk").apply {
            createNewFile()
        }

        val result = BackupUtil.copyBackupToCache(emptyFile.absolutePath, mockContext)

        result.isFailure shouldBe true
        result.exceptionOrNull().shouldBeInstanceOf<IllegalArgumentException>()
    }

    @Test
    fun `copyBackupToCache returns failure for file exceeding max size`(@TempDir tempDir: File) {
        val largeFile = File(tempDir, "backup.tachibk")
        // Create a file larger than MAX_BACKUP_SIZE
        largeFile.outputStream().use { output ->
            val buffer = ByteArray(1024 * 1024) // 1MB buffer
            val iterations = (BackupConstants.MAX_BACKUP_SIZE / buffer.size).toInt() + 2
            repeat(iterations) {
                output.write(buffer)
            }
        }

        val result = BackupUtil.copyBackupToCache(largeFile.absolutePath, mockContext)

        result.isFailure shouldBe true
        result.exceptionOrNull().shouldBeInstanceOf<IllegalArgumentException>()
        result.exceptionOrNull()?.message shouldContain "Backup file too large"
    }

    @Test
    fun `copyBackupToCache returns failure when insufficient disk space`(@TempDir tempDir: File) {
        val sourceFile = File(tempDir, "backup.tachibk").apply {
            writeText("backup content")
        }

        // Mock insufficient disk space
        every { anyConstructed<StatFs>().availableBlocksLong } returns 10L
        every { anyConstructed<StatFs>().blockSizeLong } returns 1024L // Only 10KB available

        val result = BackupUtil.copyBackupToCache(sourceFile.absolutePath, mockContext)

        result.isFailure shouldBe true
        result.exceptionOrNull().shouldBeInstanceOf<IllegalStateException>()
        result.exceptionOrNull()?.message shouldContain "Insufficient disk space"
    }

    @Test
    fun `copyBackupToCache successfully copies valid tachibk file`(@TempDir tempDir: File) {
        val sourceFile = File(tempDir, "backup.tachibk").apply {
            writeText("valid backup content")
        }

        val result = BackupUtil.copyBackupToCache(sourceFile.absolutePath, mockContext)

        // Print error for debugging if test fails
        if (result.isFailure) {
            println("Error: ${result.exceptionOrNull()?.message}")
            result.exceptionOrNull()?.printStackTrace()
        }

        result.isSuccess shouldBe true
        val copiedPath = result.getOrNull()!!
        copiedPath shouldContain "temp_backup_"
        copiedPath shouldContain ".tachibk"

        // Verify file was copied to cache
        val copiedFiles = tempCacheDir.listFiles()?.filter {
            it.name.startsWith(BackupConstants.TEMP_BACKUP_PREFIX)
        } ?: emptyList()
        copiedFiles.size shouldBe 1
        copiedFiles.first().readText() shouldBe "valid backup content"
    }

    @Test
    fun `copyBackupToCache successfully copies valid proto gz file`(@TempDir tempDir: File) {
        val sourceFile = File(tempDir, "backup.proto.gz").apply {
            writeText("valid backup content")
        }

        val result = BackupUtil.copyBackupToCache(sourceFile.absolutePath, mockContext)

        result.isSuccess shouldBe true
        val copiedPath = result.getOrNull()!!
        copiedPath shouldContain "temp_backup_"

        val copiedFiles = tempCacheDir.listFiles()?.filter {
            it.name.startsWith(BackupConstants.TEMP_BACKUP_PREFIX)
        } ?: emptyList()
        copiedFiles.size shouldBe 1
        copiedFiles.first().readText() shouldBe "valid backup content"
    }

    @Test
    fun `copyBackupToCache creates unique filenames`(@TempDir tempDir: File) {
        val sourceFile = File(tempDir, "backup.tachibk").apply {
            writeText("backup content")
        }

        val result1 = BackupUtil.copyBackupToCache(sourceFile.absolutePath, mockContext)
        val result2 = BackupUtil.copyBackupToCache(sourceFile.absolutePath, mockContext)

        result1.isSuccess shouldBe true
        result2.isSuccess shouldBe true

        // Paths should be different due to unique timestamps and UUIDs
        val path1 = result1.getOrNull()!!
        val path2 = result2.getOrNull()!!
        (path1 == path2) shouldBe false

        val copiedFiles = tempCacheDir.listFiles()?.filter {
            it.name.startsWith(BackupConstants.TEMP_BACKUP_PREFIX)
        } ?: emptyList()
        copiedFiles.size shouldBe 2
    }

    @Test
    fun `copyBackupToCache cleans old temp backups`(@TempDir tempDir: File) {
        // Create old temp backup files
        val oldFile1 = File(tempCacheDir, "${BackupConstants.TEMP_BACKUP_PREFIX}old1.tachibk").apply {
            writeText("old backup 1")
            setLastModified(System.currentTimeMillis() - BackupConstants.TEMP_BACKUP_RETENTION_MS - 1000)
        }
        val oldFile2 = File(tempCacheDir, "${BackupConstants.TEMP_BACKUP_PREFIX}old2.tachibk").apply {
            writeText("old backup 2")
            setLastModified(System.currentTimeMillis() - BackupConstants.TEMP_BACKUP_RETENTION_MS - 2000)
        }

        val sourceFile = File(tempDir, "backup.tachibk").apply {
            writeText("new backup content")
        }

        // Trigger cleanup by copying 10 times (cleanup happens every 10 copies)
        repeat(10) {
            BackupUtil.copyBackupToCache(sourceFile.absolutePath, mockContext)
        }

        // Wait a bit for async cleanup to complete
        Thread.sleep(500)

        // Old files should have been cleaned up
        oldFile1.exists() shouldBe false
        oldFile2.exists() shouldBe false
    }
}
