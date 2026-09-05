package mihon.desktop.extension

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class WindowsExtensionProcessManagerTest {

    @Test
    fun `process manager launches host, pings, handles queries, and shuts down`(@TempDir tempDir: Path) {
        runBlocking {
            val workingDir = tempDir.resolve("ext-work").toFile()
            val manager = WindowsExtensionProcessManager(workingDirectory = workingDir)
            try {
                manager.start()
                manager.state shouldBe HostProcessState.RUNNING

                val isAlive = manager.ping()
                isAlive shouldBe true

                val sources = manager.getSources()
                sources.shouldBeEmpty()

                // Test restart
                manager.restart()
                manager.state shouldBe HostProcessState.RUNNING
                manager.ping() shouldBe true
            } finally {
                manager.close()
                manager.state shouldBe HostProcessState.STOPPED
            }
        }
    }

    @Test
    fun `process manager traps crash and marks state as crashed`(@TempDir tempDir: Path) {
        runBlocking {
            val workingDir = tempDir.resolve("ext-crash").toFile()
            val manager = WindowsExtensionProcessManager(workingDirectory = workingDir)
            try {
                manager.start()
                manager.state shouldBe HostProcessState.RUNNING

                // Force kill host process
                val procField = WindowsExtensionProcessManager::class.java.getDeclaredField("process")
                procField.isAccessible = true
                val proc = procField.get(manager) as Process
                proc.destroyForcibly()
                proc.waitFor()

                delay(100)
                manager.state shouldBe HostProcessState.CRASHED

                assertThrows<Exception> {
                    manager.getSources()
                }
            } finally {
                manager.close()
            }
        }
    }
}
