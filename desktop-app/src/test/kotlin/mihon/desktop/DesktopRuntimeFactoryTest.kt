package mihon.desktop

import io.kotest.matchers.shouldBe
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

        val runtime = DesktopRuntimeFactory.create(
            args = arrayOf("--portable"),
            environment = mapOf("APPDATA" to tempDir.resolve("Roaming").toString()),
            executableDirectory = executableDir,
        )

        runtime.directories.root shouldBe executableDir.resolve("data").toAbsolutePath().normalize()
        Files.isDirectory(runtime.directories.cache) shouldBe true
    }

    @Test
    fun `explicit data directory is honored for smoke tests`() {
        val chosen = tempDir.resolve("smoke-data")

        val runtime = DesktopRuntimeFactory.create(
            args = arrayOf("--smoke-test", "--data-dir=$chosen"),
            environment = mapOf("APPDATA" to "   "),
            executableDirectory = tempDir.resolve("bin"),
        )

        runtime.smokeTest shouldBe true
        runtime.directories.root shouldBe chosen.toAbsolutePath().normalize()
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
}
