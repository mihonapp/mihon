package mihon.extension.host

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Path

class ExtensionTemporaryFilesTest {
    @Test
    fun `temporary files use requested directory and remain unique`(@TempDir directory: Path) {
        val first = ExtensionTemporaryFiles.createTempFile("filters-", null, directory.toFile())
        val second = ExtensionTemporaryFiles.createTempFile("filters-", ".json", directory.toFile())
        assertEquals(directory, first.toPath().parent)
        assertTrue(first.name.startsWith("filters-") && first.name.endsWith(".tmp"))
        assertTrue(first.isFile && second.isFile)
        assertNotEquals(first, second)
        assertThrows(IllegalArgumentException::class.java) {
            ExtensionTemporaryFiles.createTempFile("a", ".tmp", directory.toFile())
        }
        assertThrows(IOException::class.java) {
            ExtensionTemporaryFiles.createTempFile("filters-", "/escape", directory.toFile())
        }
        assertThrows(IOException::class.java) {
            ExtensionTemporaryFiles.createTempFile("filters-", ".tmp", directory.resolve("missing").toFile())
        }
    }
}
