package mihon.desktop.cli

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mihon.desktop.DesktopRuntime
import mihon.desktop.library.backup.BackupDecodeException
import mihon.desktop.library.backup.BackupValidationException
import mihon.desktop.library.local.LocalImportRejected
import mihon.desktop.library.model.ImportCounts
import mihon.desktop.library.model.ImportReport
import mihon.desktop.library.model.LibraryManga
import java.io.IOException
import java.io.OutputStream
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Path

class DesktopCommandRunner(
    private val runtime: DesktopRuntime,
    private val output: OutputStream = System.out,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    fun run(command: DesktopCommand): Int = try {
        when (command) {
            DesktopCommand.LaunchUi -> error("LaunchUi is not a headless command")
            DesktopCommand.FoundationSmoke -> {
                writeUtf8Line(output, "MIHON_DESKTOP_SMOKE_OK ${runtime.directories.root}")
                0
            }
            is DesktopCommand.ImportBackup -> {
                writeReport("import-backup", command.path, runtime.backupImporter.import(command.path, nowMillis()))
                0
            }
            is DesktopCommand.ImportLocal -> {
                val report = runtime.localImporter.import(command.path, runtime.localLibraryRoot, nowMillis())
                writeReport("import-local", command.path, report)
                0
            }
            DesktopCommand.ListLibraryJson -> {
                writeJson(ListLibraryOutput(items = runtime.library.librarySnapshot().map(LibraryItemOutput::from)))
                0
            }
        }
    } catch (error: BackupDecodeException) {
        writeRejected(command, error.kind.name, command.sourcePath())
        2
    } catch (error: BackupValidationException) {
        writeRejected(command, "BACKUP_VALIDATION", error.path)
        2
    } catch (_: LocalImportRejected) {
        writeRejected(command, "LOCAL_IMPORT_REJECTED", command.sourcePath())
        2
    } catch (_: IOException) {
        writeRejected(command, "INPUT_IO", command.sourcePath())
        2
    } catch (_: Throwable) {
        writeFailure(command.commandName(), command.sourcePath())
        1
    }

    private fun writeReport(command: String, path: Path, report: ImportReport) {
        writeJson(
            ImportOutput(
                command = command,
                status = report.status.name,
                reportId = report.id,
                path = path.toString(),
                counts = CountsOutput.from(report.counts),
            ),
        )
    }

    private fun writeRejected(command: DesktopCommand, category: String, path: String?) {
        writeJson(ErrorOutput(command.commandName(), "REJECTED", category, path))
    }

    private fun writeFailure(command: String, path: String?) {
        writeJson(ErrorOutput(command, "FAILED", "UNEXPECTED", path))
    }

    private inline fun <reified T> writeJson(value: T) {
        writeUtf8Line(output, JSON.encodeToString(value))
    }

    companion object {
        private val JSON = Json {
            encodeDefaults = true
            explicitNulls = true
        }

        fun writeCommandLineError(output: OutputStream, error: CommandLineException) {
            writeUtf8Line(
                output,
                JSON.encodeToString(ErrorOutput("command-line", "REJECTED", "COMMAND_LINE", error.argument)),
            )
        }

        fun writeStartupFailure(output: OutputStream) {
            writeUtf8Line(output, JSON.encodeToString(ErrorOutput("startup", "FAILED", "UNEXPECTED", null)))
        }
    }
}

@Serializable
private data class ImportOutput(
    val command: String,
    val status: String,
    val reportId: Long,
    val path: String,
    val counts: CountsOutput,
)

@Serializable
private data class CountsOutput(
    val mangaInserted: Long = 0,
    val mangaMerged: Long = 0,
    val chaptersInserted: Long = 0,
    val chaptersMerged: Long = 0,
    val categoriesLinked: Long = 0,
    val preferencesImported: Long = 0,
    val preferencesSkipped: Long = 0,
) {
    companion object {
        fun from(counts: ImportCounts) = CountsOutput(
            mangaInserted = counts.mangaInserted,
            mangaMerged = counts.mangaMerged,
            chaptersInserted = counts.chaptersInserted,
            chaptersMerged = counts.chaptersMerged,
            categoriesLinked = counts.categoriesLinked,
            preferencesImported = counts.preferencesImported,
            preferencesSkipped = counts.preferencesSkipped,
        )
    }
}

@Serializable
private data class ListLibraryOutput(
    val command: String = "list-library",
    val items: List<LibraryItemOutput>,
)

@Serializable
private data class LibraryItemOutput(
    val id: Long,
    val sourceId: Long,
    val url: String,
    val title: String,
    val thumbnailUrl: String?,
    val chapterCount: Long,
    val unreadCount: Long,
) {
    companion object {
        fun from(manga: LibraryManga) = LibraryItemOutput(
            id = manga.id,
            sourceId = manga.sourceId,
            url = manga.url,
            title = manga.title,
            thumbnailUrl = manga.thumbnailUrl,
            chapterCount = manga.chapterCount,
            unreadCount = manga.unreadCount,
        )
    }
}

@Serializable
private data class ErrorOutput(
    val command: String,
    val status: String,
    val category: String,
    val path: String?,
)

private fun DesktopCommand.commandName(): String = when (this) {
    DesktopCommand.LaunchUi -> "launch-ui"
    DesktopCommand.FoundationSmoke -> "foundation-smoke"
    is DesktopCommand.ImportBackup -> "import-backup"
    is DesktopCommand.ImportLocal -> "import-local"
    DesktopCommand.ListLibraryJson -> "list-library"
}

private fun DesktopCommand.sourcePath(): String? = when (this) {
    is DesktopCommand.ImportBackup -> path.toString()
    is DesktopCommand.ImportLocal -> path.toString()
    else -> null
}

private fun writeUtf8Line(output: OutputStream, value: String) {
    output.write(value.toByteArray(UTF_8))
    output.write('\n'.code)
    output.flush()
}
