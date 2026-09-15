package mihon.desktop.platform

import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/** No tasks are registered by construction. Call reconcile only after the user enables background execution. */
class WindowsBackgroundScheduler(
    private val executable: Path,
    private val profileDirectory: Path,
    private val runner: BackgroundProcessRunner = NativeBackgroundProcessRunner(),
    private val userId: String = currentWindowsUser(),
    private val namespace: String = "MihonW",
) {
    init {
        require(namespace.matches(Regex("MihonW(?:-[A-Za-z0-9-]+)?")))
    }

    fun taskName(kind: BackgroundTaskKind): String {
        val identity = profileDirectory.toAbsolutePath().normalize().toString().lowercase()
        val hash = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray())
            .take(8).joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return "$namespace-$hash-${kind.name}"
    }

    @Volatile
    var lastFailure: String? = null
        private set

    fun reconcile(
        enabled: Boolean,
        updateIntervalHours: Int,
        backupIntervalHours: Int,
    ): Map<BackgroundTaskKind, BackgroundProcessResult> = try {
        listOf(
            BackgroundTaskKind.LibraryUpdate to updateIntervalHours,
            BackgroundTaskKind.Backup to backupIntervalHours,
        ).associate { (kind, interval) ->
            kind to if (enabled && interval > 0) register(kind, interval) else remove(kind)
        }.also { results ->
            lastFailure = results.values.filter { it.exitCode != 0 }.joinToString("\n") { it.output }.ifBlank { null }
        }
    } catch (error: Exception) {
        lastFailure = error.message
        throw error
    }

    fun register(kind: BackgroundTaskKind, intervalHours: Int): BackgroundProcessResult {
        require(Files.isRegularFile(executable)) { "Application executable is missing: $executable" }
        val xml = definition(kind, intervalHours)
        val temporary = Files.createTempFile("mihon-background-", ".xml")
        try {
            Files.write(temporary, byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + xml.toByteArray(Charsets.UTF_16LE))
            return checked(listOf("/Create", "/TN", taskName(kind), "/XML", temporary.toString(), "/F"))
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    /** XML is returned for UI diagnostics; nonzero results are surfaced instead of being silently treated as absent. */
    fun query(kind: BackgroundTaskKind): BackgroundTaskQuery {
        val result = runner.run(listOf("/Query", "/TN", taskName(kind), "/XML"))
        return BackgroundTaskQuery(taskName(kind), result.exitCode, result.output)
    }

    fun remove(kind: BackgroundTaskKind): BackgroundProcessResult {
        val current = query(kind)
        if (current.isMissing) return BackgroundProcessResult(0, "Task is already absent")
        if (!current.exists) return BackgroundProcessResult(current.exitCode, current.xmlOrError)
        return runner.run(listOf("/Delete", "/TN", taskName(kind), "/F"))
    }

    fun removeAll(): List<BackgroundProcessResult> = BackgroundTaskKind.entries.map(::remove)

    fun runNow(kind: BackgroundTaskKind) {
        checked(listOf("/Run", "/TN", taskName(kind)))
    }

    fun definition(
        kind: BackgroundTaskKind,
        intervalHours: Int,
        startAt: LocalDateTime = LocalDateTime.now().plusMinutes(1),
    ): String {
        require(intervalHours in 1..744) { "Background interval must be between 1 and 744 hours" }
        require(userId.isNotBlank()) { "Windows task user is unavailable" }
        val arguments = quoteWindowsArgument("--data-dir=${profileDirectory.toAbsolutePath().normalize()}") +
            " " + kind.argument
        val start = startAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
        return """
            <?xml version="1.0" encoding="UTF-16"?>
            <Task version="1.2" xmlns="http://schemas.microsoft.com/windows/2004/02/mit/task">
              <RegistrationInfo><Description>MihonW ${kind.name} for ${xml(
            profileDirectory.toString(),
        )}</Description></RegistrationInfo>
              <Triggers><TimeTrigger><Repetition><Interval>PT${intervalHours}H</Interval><StopAtDurationEnd>false</StopAtDurationEnd></Repetition>
                <StartBoundary>$start</StartBoundary><Enabled>true</Enabled></TimeTrigger></Triggers>
              <Principals><Principal id="CurrentUser"><UserId>${xml(userId)}</UserId>
                <LogonType>InteractiveToken</LogonType><RunLevel>LeastPrivilege</RunLevel></Principal></Principals>
              <Settings><MultipleInstancesPolicy>IgnoreNew</MultipleInstancesPolicy>
                <DisallowStartIfOnBatteries>false</DisallowStartIfOnBatteries><StopIfGoingOnBatteries>false</StopIfGoingOnBatteries>
                <StartWhenAvailable>true</StartWhenAvailable><AllowStartOnDemand>true</AllowStartOnDemand>
                <Enabled>true</Enabled><Hidden>false</Hidden><ExecutionTimeLimit>PT6H</ExecutionTimeLimit></Settings>
              <Actions Context="CurrentUser"><Exec><Command>${xml(
            executable.toAbsolutePath().normalize().toString(),
        )}</Command>
                <Arguments>${xml(arguments)}</Arguments>
                <WorkingDirectory>${xml(
            executable.toAbsolutePath().parent.toString(),
        )}</WorkingDirectory></Exec></Actions>
            </Task>
        """.trimIndent()
    }

    private fun checked(arguments: List<String>): BackgroundProcessResult = runner.run(arguments).also {
        check(it.exitCode == 0) { "Windows Task Scheduler failed (${it.exitCode}): ${it.output}" }
    }
}

enum class BackgroundTaskKind(val argument: String) {
    LibraryUpdate("--background-update"),
    Backup("--background-backup"),
}

data class BackgroundTaskQuery(val taskName: String, val exitCode: Int, val xmlOrError: String) {
    val exists: Boolean get() = exitCode == 0
    val isMissing: Boolean get() = exitCode != 0 && listOf(
        "cannot find the file specified",
        "系统找不到指定的文件",
        "系統找不到指定的檔案",
    ).any { xmlOrError.contains(it, ignoreCase = true) }
}

data class BackgroundProcessResult(val exitCode: Int, val output: String)

fun interface BackgroundProcessRunner {
    fun run(arguments: List<String>): BackgroundProcessResult
}

class NativeBackgroundProcessRunner : BackgroundProcessRunner {
    override fun run(arguments: List<String>): BackgroundProcessResult {
        check(System.getProperty("os.name").startsWith("Windows")) { "Windows Task Scheduler is unavailable" }
        val systemRoot = Path.of(requireNotNull(System.getenv("SystemRoot")))
        val command = listOf(systemRoot.resolve("System32/schtasks.exe").toString()) + arguments
        val output = Files.createTempFile("mihon-schtasks-", ".log")
        try {
            val process = ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile()).start()
            try {
                check(process.waitFor(30, TimeUnit.SECONDS)) { "Windows Task Scheduler command timed out" }
                val bytes = Files.readAllBytes(output)
                val text = if (bytes.size >= 2 && bytes[0] == (-1).toByte() && bytes[1] == (-2).toByte()) {
                    String(bytes, Charsets.UTF_16LE).removePrefix("\uFEFF")
                } else {
                    String(
                        bytes,
                        Charset.forName(System.getProperty("native.encoding", Charset.defaultCharset().name())),
                    )
                }
                return BackgroundProcessResult(process.exitValue(), text.trim())
            } finally {
                if (process.isAlive) process.destroyForcibly().waitFor(5, TimeUnit.SECONDS)
            }
        } finally {
            Files.deleteIfExists(output)
        }
    }
}

internal fun quoteWindowsArgument(value: String): String {
    require('\u0000' !in value && '\n' !in value && '\r' !in value)
    val result = StringBuilder("\"")
    var slashes = 0
    for (character in value) {
        when (character) {
            '\\' -> slashes++
            '"' -> {
                result.append("\\".repeat(slashes * 2 + 1)).append('"')
                slashes = 0
            }
            else -> {
                result.append("\\".repeat(slashes)).append(character)
                slashes = 0
            }
        }
    }
    return result.append("\\".repeat(slashes * 2)).append('"').toString()
}

private fun xml(value: String): String = value.replace("&", "&amp;").replace("<", "&lt;")
    .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")

private fun currentWindowsUser(): String {
    val user = System.getenv("USERNAME") ?: System.getProperty("user.name")
    val domain = System.getenv("USERDOMAIN")
    return if (domain.isNullOrBlank()) user else "$domain\\$user"
}
