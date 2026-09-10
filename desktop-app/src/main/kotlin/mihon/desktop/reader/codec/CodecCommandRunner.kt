package mihon.desktop.reader.codec

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import mihon.reader.source.ReaderFailure
import java.io.IOException
import java.io.InputStream
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

data class CodecCommand(
    val executable: Path,
    val arguments: List<String>,
    val workingDirectory: Path,
    val timeoutMillis: Long,
    val maxStdoutBytes: Int,
    val maxStderrBytes: Int = 64 * 1024,
)

data class CodecCommandResult(
    val exitCode: Int,
    val stdout: ByteArray,
    val stderr: ByteArray,
)

fun interface CodecCommandRunner {
    suspend fun run(command: CodecCommand): CodecCommandResult
}

object ProcessCodecCommandRunner : CodecCommandRunner {
    override suspend fun run(command: CodecCommand): CodecCommandResult = runInterruptible(Dispatchers.IO) {
        val executable = command.executable.toAbsolutePath().normalize()
        val codecHome = requireNotNull(executable.parent) { "codec executable must have a parent directory" }
        val processBuilder = ProcessBuilder(listOf(executable.toString()) + command.arguments)
            .directory(command.workingDirectory.toFile())
        processBuilder.environment().apply {
            this["MAGICK_HOME"] = codecHome.toString()
            this["MAGICK_CONFIGURE_PATH"] = codecHome.toString()
            this["MAGICK_OCL_DEVICE"] = "OFF"
            this["PATH"] = codecHome.toString()
        }
        val process = try {
            processBuilder.start()
        } catch (error: IOException) {
            throw ReaderFailure.CorruptImage(error)
        }
        val stdout = CompletableFuture.supplyAsync {
            process.inputStream.readBounded(command.maxStdoutBytes, "codec stdout")
        }
        val stderr = CompletableFuture.supplyAsync {
            process.errorStream.readBounded(command.maxStderrBytes, "codec stderr")
        }
        try {
            if (!process.waitFor(command.timeoutMillis, TimeUnit.MILLISECONDS)) {
                throw ReaderFailure.CorruptImage(IllegalStateException("codec process timed out"))
            }
            CodecCommandResult(process.exitValue(), stdout.get(), stderr.get())
        } finally {
            if (process.isAlive) {
                process.descendants().forEach { it.destroyForcibly() }
                process.destroyForcibly()
            }
        }
    }

    private fun InputStream.readBounded(limit: Int, name: String): ByteArray = use {
        val bytes = readNBytes(Math.addExact(limit, 1))
        if (bytes.size > limit) throw ReaderFailure.LimitExceeded(name, limit.toLong(), bytes.size.toLong())
        bytes
    }
}
