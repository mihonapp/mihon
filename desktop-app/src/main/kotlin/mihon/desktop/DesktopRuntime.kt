package mihon.desktop

import mihon.desktop.cli.CommandLineException
import mihon.desktop.cli.DesktopCommand
import mihon.desktop.cli.DesktopCommandParser
import mihon.desktop.library.backup.AndroidBackupCodec
import mihon.desktop.library.backup.AndroidBackupImporter
import mihon.desktop.library.backup.AndroidBackupValidator
import mihon.desktop.library.db.DesktopLibraryDatabaseFactory
import mihon.desktop.library.db.SqlDelightLibraryRepository
import mihon.desktop.library.local.LocalMangaImporter
import mihon.desktop.platform.AppDirectories
import mihon.desktop.platform.AppDirectoryResolver
import mihon.desktop.platform.DistributionMode
import mihon.desktop.preferences.DesktopPreferenceStore
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

class DesktopRuntime(
    val directories: AppDirectories,
    val preferences: DesktopPreferenceStore,
    val command: DesktopCommand,
    val library: SqlDelightLibraryRepository,
    val backupImporter: AndroidBackupImporter,
    val localImporter: LocalMangaImporter,
    val localLibraryRoot: Path,
    internal val closeLibrary: () -> Unit = library::close,
) : AutoCloseable {
    private val closed = AtomicBoolean()

    override fun close() {
        if (closed.compareAndSet(false, true)) closeLibrary()
    }
}

object DesktopRuntimeFactory {
    fun create(
        args: Array<String>,
        environment: Map<String, String>,
        executableDirectory: Path,
    ): DesktopRuntime = create(args, environment, executableDirectory, LocalMangaImporter::cleanupOrphans)

    internal fun create(
        args: Array<String>,
        environment: Map<String, String>,
        executableDirectory: Path,
        cleanupOrphans: (LocalMangaImporter, Path) -> Unit,
    ): DesktopRuntime {
        val command = DesktopCommandParser.parse(args)
        val dataDirectoryArguments = args.filter { it.startsWith("--data-dir=") }
        if (dataDirectoryArguments.size > 1) {
            throw CommandLineException("--data-dir may be specified only once", "--data-dir")
        }
        val explicitRoot = dataDirectoryArguments.singleOrNull()?.let { argument ->
            val value = argument.substringAfter('=')
            if (value.isBlank()) throw CommandLineException("--data-dir requires a non-blank path", "--data-dir")
            commandPath(value, "--data-dir")
        }
        val mode = if ("--portable" in args) DistributionMode.Portable else DistributionMode.Installed
        val appData = environment["APPDATA"]
            ?.takeIf(String::isNotBlank)
            ?.let(Path::of)
        val directories = AppDirectoryResolver(appData, executableDirectory)
            .resolve(mode, explicitRoot)
            .create()
        val library = DesktopLibraryDatabaseFactory.open(directories.database.resolve("library.db"))
        try {
            val backupImporter = AndroidBackupImporter(AndroidBackupCodec(), AndroidBackupValidator(), library)
            val localImporter = LocalMangaImporter(library)
            val localLibraryRoot = directories.root.resolve("media").resolve("local").toAbsolutePath().normalize()
            cleanupOrphans(localImporter, localLibraryRoot)
            return DesktopRuntime(
                directories = directories,
                preferences = DesktopPreferenceStore(directories.root.resolve("preferences.properties")),
                command = command,
                library = library,
                backupImporter = backupImporter,
                localImporter = localImporter,
                localLibraryRoot = localLibraryRoot,
            )
        } catch (error: Throwable) {
            try {
                library.close()
            } catch (closeError: Throwable) {
                error.addSuppressed(closeError)
            }
            throw error
        }
    }

    private fun commandPath(value: String, option: String): Path = try {
        Path.of(value)
    } catch (error: RuntimeException) {
        throw CommandLineException("$option contains an invalid path", option, error)
    }
}
