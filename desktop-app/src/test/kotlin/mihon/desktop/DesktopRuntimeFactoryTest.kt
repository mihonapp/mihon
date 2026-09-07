package mihon.desktop

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import mihon.desktop.cli.DesktopCommand
import mihon.desktop.reader.DesktopReaderFactory
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.awt.EventQueue
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

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

    @Test
    fun `UI runtime owns isolated sessions with one shared factory and global generations`() {
        runBlocking {
            val runtime = DesktopRuntimeFactory.create(
                args = emptyArray(),
                environment = mapOf("APPDATA" to tempDir.resolve("Roaming").toString()),
                executableDirectory = tempDir.resolve("bin"),
            )
            val factory = requireNotNull(runtime.readerFactory)

            val first = factory.createSession()
            val second = factory.createSession()

            val generations = coroutineScope {
                List(64) { async(Dispatchers.Default) { factory.nextGeneration() } }.awaitAll()
            }
            generations.sorted() shouldBe (0L until 64L).toList()
            (first === second) shouldBe false
            runtime.shutdown()
            org.junit.jupiter.api.assertThrows<IllegalStateException> { factory.createSession() }
        }
    }

    @Test
    fun `foundation verification remains headless without reader services`() {
        DesktopRuntimeFactory.create(
            args = arrayOf("--smoke-test"),
            environment = mapOf("APPDATA" to tempDir.resolve("Roaming").toString()),
            executableDirectory = tempDir.resolve("bin"),
        ).use { runtime ->
            runtime.readerFactory shouldBe null
        }
    }

    @Test
    fun `suspending shutdown closes sessions before services and database while suppressing failures`() {
        runBlocking {
            val events = mutableListOf<String>()
            val firstFailure = IllegalStateException("first")
            val secondFailure = IllegalArgumentException("second")
            val runtime = DesktopRuntime.forTesting(
                closeSessions = {
                    events += "sessions"
                    throw firstFailure
                },
                closeReaderServices = {
                    events += "services"
                    throw secondFailure
                },
                closeLibrary = { events += "library" },
            )

            val thrown = try {
                runtime.shutdown()
                error("shutdown must surface the first failure")
            } catch (error: IllegalStateException) {
                error
            }

            thrown shouldBe firstFailure
            thrown.suppressed.toList() shouldBe listOf(secondFailure)
            events shouldBe listOf("sessions", "services", "library")
            val repeated = try {
                runtime.shutdown()
                error("repeated shutdown must preserve the first failure")
            } catch (error: IllegalStateException) {
                error
            }
            repeated shouldBe firstFailure
            events shouldBe listOf("sessions", "services", "library")
        }
    }

    @Test
    fun `fallback close uses shutdown once away from the EDT and rejects the EDT`() {
        val closes = AtomicInteger()
        val runtime = DesktopRuntime.forTesting(closeLibrary = closes::incrementAndGet)

        runtime.close()
        runtime.close()

        closes.get() shouldBe 1
        EventQueue.invokeAndWait {
            org.junit.jupiter.api.assertThrows<IllegalStateException> { runtime.close() }
        }
    }

    @Test
    fun `runtime initializes sourceManager with bundled MangaDex source`() {
        val runtime = DesktopRuntimeFactory.create(
            args = emptyArray(),
            environment = mapOf("APPDATA" to tempDir.resolve("Roaming").toString()),
            executableDirectory = tempDir.resolve("bin"),
        )
        try {
            val sources = runtime.sourceManager.getSources()
            sources.any { it.name.contains("MangaDex") } shouldBe true
        } finally {
            runtime.close()
        }
    }
}
