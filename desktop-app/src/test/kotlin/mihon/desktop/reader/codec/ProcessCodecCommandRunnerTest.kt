package mihon.desktop.reader.codec

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
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
