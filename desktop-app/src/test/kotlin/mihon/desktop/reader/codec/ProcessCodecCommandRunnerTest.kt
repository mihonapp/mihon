package mihon.desktop.reader.codec

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import mihon.reader.source.ReaderFailure
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class ProcessCodecCommandRunnerTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `missing executable becomes a typed page failure`() = runTest {
        shouldThrow<ReaderFailure.CorruptImage> {
            ProcessCodecCommandRunner.run(
                command(temporaryDirectory.resolve("missing-codec.exe"), listOf("-version")),
            )
        }
    }

    @Test
    fun `timeout kills request and a later process still succeeds`() = runTest {
        val commandPrompt = Path.of(System.getenv("SystemRoot"), "System32", "cmd.exe")
        shouldThrow<ReaderFailure.CorruptImage> {
            ProcessCodecCommandRunner.run(
                command(
                    commandPrompt,
                    listOf("/d", "/c", "ping -n 6 127.0.0.1 >nul"),
                    timeoutMillis = 100,
                ),
            )
        }

        val result = ProcessCodecCommandRunner.run(
            command(commandPrompt, listOf("/d", "/c", "echo recovered")),
        )
        result.exitCode shouldBe 0
        result.stdout.decodeToString().trim() shouldBe "recovered"
    }

    @Test
    fun `cancellation interrupts and destroys a running request`() = runTest {
        val powerShell = Path.of(System.getProperty("java.home"))
            .parent
            .resolve("pwsh.exe")
            .takeIf { it.toFile().isFile }
            ?: Path.of("C:/Program Files/PowerShell/7/pwsh.exe")
        val startedAt = System.nanoTime()
        val job = launch {
            ProcessCodecCommandRunner.run(
                command(
                    powerShell,
                    listOf("-NoProfile", "-Command", "Start-Sleep -Seconds 30"),
                    timeoutMillis = 60_000,
                ),
            )
        }
        delay(200)
        job.cancelAndJoin()
        val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000
        (elapsedMillis < 5_000) shouldBe true
    }

    private fun command(
        executable: Path,
        arguments: List<String>,
        timeoutMillis: Long = 2_000,
    ) = CodecCommand(
        executable = executable,
        arguments = arguments,
        workingDirectory = temporaryDirectory,
        timeoutMillis = timeoutMillis,
        maxStdoutBytes = 1024,
    )
}
