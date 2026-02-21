package eu.kanade.presentation.components

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import java.io.File

@Execution(ExecutionMode.CONCURRENT)
class FileSystemNavigatorTest {

    private val navigator = FileSystemNavigator()

    @Test
    fun `listDirectory returns empty list for empty directory`(@TempDir tempDir: File) {
        val result = navigator.listDirectory(tempDir.absolutePath, PickerMode.FOLDER)

        result.isSuccess shouldBe true
        result.getOrNull().shouldBeEmpty()
    }

    @Test
    fun `listDirectory returns failure for non-existent path`(@TempDir tempDir: File) {
        val nonExistent = File(tempDir, "nonexistent").absolutePath

        val result = navigator.listDirectory(nonExistent, PickerMode.FOLDER)

        result.isFailure shouldBe true
        result.exceptionOrNull().shouldBeInstanceOf<IllegalArgumentException>()
    }

    @Test
    fun `listDirectory returns failure for file path`(@TempDir tempDir: File) {
        val file = File(tempDir, "test.txt").apply { writeText("content") }

        val result = navigator.listDirectory(file.absolutePath, PickerMode.FOLDER)

        result.isFailure shouldBe true
        result.exceptionOrNull().shouldBeInstanceOf<IllegalArgumentException>()
    }

    @Test
    fun `listDirectory lists only folders in FOLDER mode`(@TempDir tempDir: File) {
        // Create folders and files
        File(tempDir, "folder1").mkdir()
        File(tempDir, "folder2").mkdir()
        File(tempDir, "file.txt").writeText("content")
        File(tempDir, "backup.tachibk").writeText("backup")

        val result = navigator.listDirectory(tempDir.absolutePath, PickerMode.FOLDER)

        result.isSuccess shouldBe true
        val items = result.getOrNull()!!
        items shouldHaveSize 2
        items.all { it.type == PickerItemType.FOLDER } shouldBe true
        items.map { it.file.name }.sorted() shouldBe listOf("folder1", "folder2")
    }

    @Test
    fun `listDirectory lists folders and backup files in FILE mode`(@TempDir tempDir: File) {
        // Create folders and files
        File(tempDir, "folder1").mkdir()
        File(tempDir, "backup1.tachibk").writeText("backup")
        File(tempDir, "backup2.proto.gz").writeText("backup")
        File(tempDir, "regular.txt").writeText("content")

        val result = navigator.listDirectory(tempDir.absolutePath, PickerMode.FILE)

        result.isSuccess shouldBe true
        val items = result.getOrNull()!!
        items shouldHaveSize 3
        items.count { it.type == PickerItemType.FOLDER } shouldBe 1
        items.count { it.type == PickerItemType.FILE } shouldBe 2
    }

    @Test
    fun `listDirectory sorts folders before files`(@TempDir tempDir: File) {
        File(tempDir, "z_folder").mkdir()
        File(tempDir, "a_backup.tachibk").writeText("backup")
        File(tempDir, "m_folder").mkdir()

        val result = navigator.listDirectory(tempDir.absolutePath, PickerMode.FILE)

        result.isSuccess shouldBe true
        val items = result.getOrNull()!!
        items shouldHaveSize 3
        // Folders should come first
        items[0].type shouldBe PickerItemType.FOLDER
        items[1].type shouldBe PickerItemType.FOLDER
        items[2].type shouldBe PickerItemType.FILE
        // Within folders, alphabetically sorted
        items[0].file.name shouldBe "m_folder"
        items[1].file.name shouldBe "z_folder"
    }

    @Test
    fun `createFolder creates folder successfully`(@TempDir tempDir: File) {
        val result = navigator.createFolder(tempDir.absolutePath, "new_folder")

        result.isSuccess shouldBe true
        val folder = result.getOrNull()!!
        folder.exists() shouldBe true
        folder.isDirectory shouldBe true
        folder.name shouldBe "new_folder"
    }

    @Test
    fun `createFolder returns failure for empty name`(@TempDir tempDir: File) {
        val result = navigator.createFolder(tempDir.absolutePath, "")

        result.isFailure shouldBe true
        result.exceptionOrNull().shouldBeInstanceOf<IllegalArgumentException>()
    }

    @Test
    fun `createFolder returns failure for blank name`(@TempDir tempDir: File) {
        val result = navigator.createFolder(tempDir.absolutePath, "   ")

        result.isFailure shouldBe true
        result.exceptionOrNull().shouldBeInstanceOf<IllegalArgumentException>()
    }

    @Test
    fun `createFolder returns failure for name with path separator`(@TempDir tempDir: File) {
        val result = navigator.createFolder(tempDir.absolutePath, "folder/subfolder")

        result.isFailure shouldBe true
        result.exceptionOrNull().shouldBeInstanceOf<IllegalArgumentException>()
    }

    @Test
    fun `createFolder returns failure if folder already exists`(@TempDir tempDir: File) {
        val existingFolder = File(tempDir, "existing").apply { mkdir() }

        val result = navigator.createFolder(tempDir.absolutePath, "existing")

        result.isFailure shouldBe true
        result.exceptionOrNull().shouldBeInstanceOf<IllegalArgumentException>()
    }

    @Test
    fun `createFolder returns failure for non-existent parent`(@TempDir tempDir: File) {
        val nonExistentParent = File(tempDir, "nonexistent").absolutePath

        val result = navigator.createFolder(nonExistentParent, "new_folder")

        result.isFailure shouldBe true
        result.exceptionOrNull().shouldBeInstanceOf<IllegalArgumentException>()
    }

    @Test
    fun `getParentPath returns parent directory`(@TempDir tempDir: File) {
        val childDir = File(tempDir, "child")
        val parent = navigator.getParentPath(childDir.absolutePath)

        parent shouldBe tempDir.absolutePath
    }

    @Test
    fun `getParentPath returns null for root`() {
        val parent = navigator.getParentPath("/")

        parent shouldBe null
    }

    @Test
    fun `validatePath succeeds for existing readable path`(@TempDir tempDir: File) {
        val result = navigator.validatePath(tempDir.absolutePath)

        result.isSuccess shouldBe true
    }

    @Test
    fun `validatePath fails for non-existent path`(@TempDir tempDir: File) {
        val nonExistent = File(tempDir, "nonexistent").absolutePath
        val result = navigator.validatePath(nonExistent)

        result.isFailure shouldBe true
        result.exceptionOrNull().shouldBeInstanceOf<IllegalArgumentException>()
    }
}
