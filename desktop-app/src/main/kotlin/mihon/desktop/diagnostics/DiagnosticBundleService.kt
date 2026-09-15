package mihon.desktop.diagnostics

import mihon.desktop.library.repository.LibraryRepository
import mihon.desktop.platform.AppDirectories
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class DiagnosticSummary(
    val timestamp: String,
    val osName: String,
    val osVersion: String,
    val osArch: String,
    val javaVersion: String,
    val javaVendor: String,
    val totalMemoryBytes: Long,
    val maxMemoryBytes: Long,
    val freeMemoryBytes: Long,
    val databaseIntegrity: List<String>,
    val logFileCount: Int,
)

class DiagnosticBundleService(
    private val directories: AppDirectories,
    private val repository: LibraryRepository,
) {
    fun createSummary(): DiagnosticSummary {
        val runtime = Runtime.getRuntime()
        val integrity = try {
            repository.checkIntegrity()
        } catch (e: Throwable) {
            listOf("ERROR: ${e.message}")
        }
        val logFiles = if (Files.exists(directories.logs)) {
            Files.list(directories.logs).use { it.count().toInt() }
        } else {
            0
        }

        return DiagnosticSummary(
            timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
            osName = System.getProperty("os.name", "Unknown"),
            osVersion = System.getProperty("os.version", "Unknown"),
            osArch = System.getProperty("os.arch", "Unknown"),
            javaVersion = System.getProperty("java.version", "Unknown"),
            javaVendor = System.getProperty("java.vendor", "Unknown"),
            totalMemoryBytes = runtime.totalMemory(),
            maxMemoryBytes = runtime.maxMemory(),
            freeMemoryBytes = runtime.freeMemory(),
            databaseIntegrity = integrity,
            logFileCount = logFiles,
        )
    }

    fun exportBundle(targetZip: Path): Path {
        Files.createDirectories(targetZip.parent ?: Path.of("."))
        val summary = createSummary()

        Files.newOutputStream(targetZip).use { fileOut ->
            ZipOutputStream(fileOut).use { zip ->
                // 1. summary.txt
                zip.putNextEntry(ZipEntry("diagnostics/summary.txt"))
                val summaryText = buildString {
                    appendLine("mihondesk Diagnostic Summary")
                    appendLine("==========================")
                    appendLine("Timestamp: ${summary.timestamp}")
                    appendLine("OS: ${summary.osName} ${summary.osVersion} (${summary.osArch})")
                    appendLine("Java: ${summary.javaVersion} (${summary.javaVendor})")
                    appendLine(
                        "Memory: total=${summary.totalMemoryBytes}, max=${summary.maxMemoryBytes}, free=${summary.freeMemoryBytes}",
                    )
                    appendLine("DB Integrity: ${summary.databaseIntegrity.joinToString(", ")}")
                    appendLine("Log files present: ${summary.logFileCount}")
                }
                zip.write(summaryText.toByteArray(UTF_8))
                zip.closeEntry()

                // 2. Redacted logs
                if (Files.exists(directories.logs)) {
                    Files.list(directories.logs).use { stream ->
                        stream.forEach { logFile ->
                            if (Files.isRegularFile(logFile)) {
                                val logContent = try {
                                    sanitizeLogContent(Files.readString(logFile, UTF_8))
                                } catch (_: Throwable) {
                                    "[Unreadable log file]"
                                }
                                zip.putNextEntry(ZipEntry("diagnostics/logs/${logFile.fileName}"))
                                zip.write(logContent.toByteArray(UTF_8))
                                zip.closeEntry()
                            }
                        }
                    }
                }
            }
        }
        return targetZip
    }

    private fun sanitizeLogContent(content: String): String {
        // Redact tokens, passwords, cookies, authorization headers
        return content
            .replace(Regex("""(?i)(bearer\s+)[A-Za-z0-9_\-\.]+"""), "$1[REDACTED]")
            .replace(Regex("""(?i)(password\s*[:=]\s*)[^\s,;]+"""), "$1[REDACTED]")
            .replace(Regex("""(?i)(token\s*[:=]\s*)[^\s,;]+"""), "$1[REDACTED]")
            .replace(Regex("""(?i)(session\s*[:=]\s*)[^\s,;]+"""), "$1[REDACTED]")
            .replace(Regex("""(?i)(cookie\s*[:=]\s*)[^\r\n]+"""), "$1[REDACTED]")
    }
}
