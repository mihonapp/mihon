package mihon.desktop.platform

import mihon.desktop.cli.CommandLineException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

class ProfileInUseException(val profile: Path) : IllegalStateException("Profile is already open: $profile")

/** Held from before SQLite is opened until all runtime resources have closed. Never delete the lock file. */
class DesktopProfileLock private constructor(
    private val channel: FileChannel,
    private val lock: FileLock,
) : AutoCloseable {
    override fun close() {
        try {
            if (lock.isValid) lock.release()
        } finally {
            channel.close()
        }
    }

    companion object {
        fun acquire(root: Path): DesktopProfileLock {
            Files.createDirectories(root)
            val canonical = root.toRealPath()
            val channel = FileChannel.open(
                canonical.resolve(".mihon-profile.lock"),
                StandardOpenOption.CREATE,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE,
            )
            try {
                val lock = try {
                    channel.tryLock()
                } catch (_: OverlappingFileLockException) {
                    null
                }
                    ?: throw ProfileInUseException(canonical)
                channel.truncate(0)
                channel.write(ByteBuffer.wrap(ProcessHandle.current().pid().toString().toByteArray()))
                channel.force(true)
                return DesktopProfileLock(channel, lock)
            } catch (error: Throwable) {
                channel.close()
                throw error
            }
        }
    }
}

object DesktopProfileDirectories {
    fun resolve(args: Array<String>, environment: Map<String, String>, executableDirectory: Path): AppDirectories {
        val values = args.filter { it.startsWith("--data-dir=") }
        if (values.size > 1) throw CommandLineException("--data-dir may be specified only once", "--data-dir")
        val explicit = values.singleOrNull()?.substringAfter('=')?.let { value ->
            if (value.isBlank()) throw CommandLineException("--data-dir requires a non-blank path", "--data-dir")
            try {
                Path.of(value)
            } catch (error: RuntimeException) {
                throw CommandLineException("--data-dir contains an invalid path", "--data-dir", error)
            }
        }
        val portable = "--portable" in args || Files.exists(executableDirectory.resolve(".portable"))
        return AppDirectoryResolver(
            environment["APPDATA"]?.takeIf { it.isNotBlank() }?.let(Path::of),
            executableDirectory,
        ).resolve(if (portable) DistributionMode.Portable else DistributionMode.Installed, explicit)
    }
}
