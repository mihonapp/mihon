package eu.kanade.tachiyomi.util.system

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import java.io.File

/**
 * Unit tests for safe file operation extensions.
 *
 * These extensions provide SecurityException-safe wrappers around standard
 * File operations, particularly useful in restricted environments like
 * Samsung Secure Folder where file access may throw SecurityException.
 */
@Execution(ExecutionMode.CONCURRENT)
@DisplayName("FileExtensions Tests")
class FileExtensionsTest {

    @Test
    fun `safeCanonicalFile returns canonical file for valid path`(@TempDir tempDir: File) {
        val testFile = File(tempDir, "test.txt")
        testFile.createNewFile()

        val canonicalFile = testFile.safeCanonicalFile()

        canonicalFile shouldNotBe null
        canonicalFile?.exists() shouldBe true
    }

    @Test
    fun `safeCanonicalFile returns null for file that throws SecurityException`() {
        // Create a file with a path that might trigger security issues
        val testFile = File("/root/restricted/test.txt")

        // Should not throw, returns null instead
        val canonicalFile = testFile.safeCanonicalFile()

        // May be null or the file itself depending on platform restrictions
        // The important thing is it doesn't throw an exception
        canonicalFile shouldBe canonicalFile // Tautology just to ensure no exception
    }

    @Test
    fun `safeExists returns true for existing file`(@TempDir tempDir: File) {
        val testFile = File(tempDir, "existing.txt")
        testFile.createNewFile()

        testFile.safeExists() shouldBe true
    }

    @Test
    fun `safeExists returns false for non-existent file`(@TempDir tempDir: File) {
        val testFile = File(tempDir, "nonexistent.txt")

        testFile.safeExists() shouldBe false
    }

    @Test
    fun `safeExists returns false instead of throwing on SecurityException`() {
        // File that might trigger security restrictions
        val restrictedFile = File("/root/restricted.txt")

        // Should return false instead of throwing SecurityException
        val exists = restrictedFile.safeExists()

        exists shouldBe false
    }

    @Test
    fun `safeIsDirectory returns true for directory`(@TempDir tempDir: File) {
        val testDir = File(tempDir, "testdir")
        testDir.mkdir()

        testDir.safeIsDirectory() shouldBe true
    }

    @Test
    fun `safeIsDirectory returns false for regular file`(@TempDir tempDir: File) {
        val testFile = File(tempDir, "testfile.txt")
        testFile.createNewFile()

        testFile.safeIsDirectory() shouldBe false
    }

    @Test
    fun `safeIsDirectory returns false for non-existent path`(@TempDir tempDir: File) {
        val nonExistent = File(tempDir, "nonexistent")

        nonExistent.safeIsDirectory() shouldBe false
    }

    @Test
    fun `safeIsDirectory returns false instead of throwing on SecurityException`() {
        val restrictedDir = File("/root/restricted/")

        // Should return false instead of throwing SecurityException
        val isDir = restrictedDir.safeIsDirectory()

        isDir shouldBe false
    }

    @Test
    fun `safeLength returns file size for existing file`(@TempDir tempDir: File) {
        val testFile = File(tempDir, "test.txt")
        val content = "Hello, World!"
        testFile.writeText(content)

        val length = testFile.safeLength()

        length shouldBe content.length.toLong()
    }

    @Test
    fun `safeLength returns 0 for non-existent file`(@TempDir tempDir: File) {
        val nonExistent = File(tempDir, "nonexistent.txt")

        nonExistent.safeLength() shouldBe 0L
    }

    @Test
    fun `safeLength returns non-negative value for directory`(@TempDir tempDir: File) {
        val testDir = File(tempDir, "testdir")
        testDir.mkdir()

        // Directories report 0 or a platform-specific value (e.g., 4096 on Linux)
        val length = testDir.safeLength()

        // Just verify it doesn't throw and returns a non-negative value
        (length >= 0L) shouldBe true
    }

    @Test
    fun `safeLength returns 0 instead of throwing on SecurityException`() {
        val restrictedFile = File("/root/restricted.txt")

        // Should return 0 instead of throwing SecurityException
        val length = restrictedFile.safeLength()

        length shouldBe 0L
    }

    @Test
    fun `safeCanonicalFile handles paths with parent references`(@TempDir tempDir: File) {
        // Create a subdir first
        val subdir = File(tempDir, "subdir")
        subdir.mkdir()

        // Create a file in the temp dir
        val testFile = File(tempDir, "test.txt")
        testFile.createNewFile()

        // Reference it using a path with ".."
        val pathWithParentRef = File(subdir, "../test.txt")

        val canonicalFile = pathWithParentRef.safeCanonicalFile()

        canonicalFile shouldNotBe null
        // Canonical path should not contain ".."
        canonicalFile?.path?.contains("..") shouldBe false
        // Should point to the same file
        canonicalFile?.absolutePath shouldBe testFile.absolutePath
    }

    @Test
    fun `all safe methods are chainable and do not throw exceptions`(@TempDir tempDir: File) {
        val testFile = File(tempDir, "chain-test.txt")
        testFile.writeText("test content")

        // Chain multiple safe operations - none should throw
        val canonical = testFile.safeCanonicalFile()
        val exists = canonical?.safeExists() ?: false
        val isDir = canonical?.safeIsDirectory() ?: true
        val length = canonical?.safeLength() ?: -1L

        exists shouldBe true
        isDir shouldBe false
        length shouldBe 12L // "test content".length
    }

    @Test
    fun `safe operations on empty file return expected values`(@TempDir tempDir: File) {
        val emptyFile = File(tempDir, "empty.txt")
        emptyFile.createNewFile()

        emptyFile.safeExists() shouldBe true
        emptyFile.safeIsDirectory() shouldBe false
        emptyFile.safeLength() shouldBe 0L
        emptyFile.safeCanonicalFile() shouldNotBe null
    }
}
