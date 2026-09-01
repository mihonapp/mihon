package mihon.desktop

import io.kotest.matchers.shouldBe
import mihon.desktop.cli.DesktopCommand
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class DesktopRuntimeFactoryTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `portable runtime never uses APPDATA`() {
        val executableDir = tempDir.resolve("portable")

        DesktopRuntimeFactory.create(
            args = arrayOf("--portable"),
            environment = mapOf("APPDATA" to tempDir.resolve("Roaming").toString()),
            executableDirectory = executableDir,
        ).use { runtime ->
            runtime.command shouldBe DesktopCommand.LaunchUi
            runtime.directories.root shouldBe executableDir.resolve("data").toAbsolutePath().normalize()
            Files.isDirectory(runtime.directories.cache) shouldBe true
        }
    }

    @Test
    fun `explicit data directory is honored for smoke tests`() {
        val chosen = tempDir.resolve("smoke-data")

        DesktopRuntimeFactory.create(
            args = arrayOf("--smoke-test", "--data-dir=$chosen"),
            environment = mapOf("APPDATA" to "   "),
            executableDirectory = tempDir.resolve("bin"),
        ).use { runtime ->
            runtime.command shouldBe DesktopCommand.FoundationSmoke
            runtime.directories.root shouldBe chosen.toAbsolutePath().normalize()
            Files.isRegularFile(runtime.directories.database.resolve("library.db")) shouldBe true
            runtime.localLibraryRoot shouldBe chosen.resolve("media").resolve("local").toAbsolutePath().normalize()
        }
    }

    @Test
    fun `blank APPDATA requires an explicit installed data directory`() {
        val exception = assertThrows<IllegalArgumentException> {
            DesktopRuntimeFactory.create(
                args = emptyArray(),
                environment = mapOf("APPDATA" to "   "),
                executableDirectory = tempDir.resolve("bin"),
            )
        }

        exception.message shouldBe "APPDATA is unavailable; pass --data-dir=<path> to select a writable data directory"
    }

    @Test
    fun `local orphan cleanup runs exactly once before runtime is exposed`() {
        val chosen = tempDir.resolve("cleanup-data")
        var cleanupCalls = 0

        DesktopRuntimeFactory.create(
            args = arrayOf("--list-library-json", "--data-dir=$chosen"),
            environment = emptyMap(),
            executableDirectory = tempDir.resolve("bin"),
            cleanupOrphans = { importer, localLibraryRoot ->
                cleanupCalls++
                importer.cleanupOrphans(localLibraryRoot)
            },
        ).use { runtime ->
            cleanupCalls shouldBe 1
            runtime.library.librarySnapshot() shouldBe emptyList()
        }
        cleanupCalls shouldBe 1
    }
}
