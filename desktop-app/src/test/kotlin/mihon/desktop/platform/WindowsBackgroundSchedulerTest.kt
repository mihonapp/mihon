package mihon.desktop.platform

import mihon.desktop.cli.DesktopCommandParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.UUID
import javax.xml.parsers.DocumentBuilderFactory

class WindowsBackgroundSchedulerTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `profile lock excludes another owner and is released without deleting lock file`() {
        DesktopProfileLock.acquire(directory).use {
            assertThrows(ProfileInUseException::class.java) { DesktopProfileLock.acquire(directory.resolve(".")) }
        }
        assertTrue(Files.exists(directory.resolve(".mihon-profile.lock")))
        DesktopProfileLock.acquire(directory).close()
    }

    @Test
    fun `task definition preserves Chinese spaced paths and bounded missed schedule behavior`() {
        val executable = directory.resolve("应用 程序/MihonW.exe")
        val profile = directory.resolve("我的 书库 & 数据")
        val scheduler = WindowsBackgroundScheduler(executable, profile, userId = "DOMAIN\\test")
        val xml = scheduler.definition(BackgroundTaskKind.LibraryUpdate, 12, LocalDateTime.of(2026, 9, 15, 12, 0))
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(xml.toByteArray(Charsets.UTF_16LE).inputStream())
        fun value(name: String) = document.getElementsByTagName(name).item(0).textContent
        assertEquals(executable.toAbsolutePath().toString(), value("Command"))
        assertEquals(
            quoteWindowsArgument("--data-dir=${profile.toAbsolutePath()}") + " --background-update",
            value("Arguments"),
        )
        assertEquals("PT12H", value("Interval"))
        assertEquals("IgnoreNew", value("MultipleInstancesPolicy"))
        assertEquals("true", value("StartWhenAvailable"))
        assertEquals("InteractiveToken", value("LogonType"))
        assertEquals("LeastPrivilege", value("RunLevel"))
    }

    @Test
    fun `task management has no construction side effect and updates stable profile task`() {
        val calls = mutableListOf<List<String>>()
        val executable = Files.createFile(directory.resolve("MihonW.exe"))
        val runner = BackgroundProcessRunner { arguments ->
            calls.add(arguments)
            if ("/Create" in
                arguments
            ) {
                assertTrue(Files.readString(Path.of(arguments[4]), Charsets.UTF_16LE).contains("<Task"))
            }
            BackgroundProcessResult(0, "ok")
        }
        val scheduler = WindowsBackgroundScheduler(executable, directory, runner)
        assertTrue(calls.isEmpty())
        scheduler.register(BackgroundTaskKind.Backup, 24)
        assertEquals("/Create", calls.last().first())
        assertFalse(Files.exists(Path.of(calls.last()[4])))
        assertEquals(scheduler.taskName(BackgroundTaskKind.Backup), calls.last()[2])
        assertTrue(scheduler.query(BackgroundTaskKind.Backup).exists)
        assertEquals(0, scheduler.remove(BackgroundTaskKind.Backup).exitCode)
        val moved = WindowsBackgroundScheduler(directory.resolve("new/MihonW.exe"), directory, runner)
        assertEquals(scheduler.taskName(BackgroundTaskKind.Backup), moved.taskName(BackgroundTaskKind.Backup))
        assertThrows(IllegalArgumentException::class.java) { scheduler.definition(BackgroundTaskKind.Backup, 0) }
    }

    @Test
    fun `Windows Task Scheduler registers queries and removes isolated temporary task`() {
        assumeTrue(System.getProperty("os.name").startsWith("Windows"))
        assumeTrue(System.getenv("MIHON_W_TEST_SCHEDULED_TASK") == "1")
        val scheduler = WindowsBackgroundScheduler(
            Path.of(System.getenv("SystemRoot"), "System32", "cmd.exe"),
            directory,
            namespace = "MihonW-Test-${UUID.randomUUID()}",
        )
        var registered = false
        try {
            scheduler.register(BackgroundTaskKind.Backup, 744)
            registered = true
            val query = scheduler.query(BackgroundTaskKind.Backup)
            assertTrue(query.exists, query.xmlOrError)
            assertTrue(query.xmlOrError.contains("--background-backup"), query.xmlOrError)
        } finally {
            val removed = scheduler.remove(BackgroundTaskKind.Backup)
            if (registered) assertEquals(0, removed.exitCode, removed.output)
        }
        assertFalse(scheduler.query(BackgroundTaskKind.Backup).exists)
    }

    @Test
    fun `Windows second process cannot acquire the active profile lock`() {
        assumeTrue(System.getProperty("os.name").startsWith("Windows"))
        val script = directory.resolve("lock-probe.ps1")
        Files.writeString(
            script,
            """
            param([string]${'$'}LockPath)
            try {
                ${'$'}stream = [System.IO.File]::Open(${'$'}LockPath, 'Open', 'ReadWrite', 'ReadWrite')
                ${'$'}stream.Lock(0, 1)
                ${'$'}stream.Unlock(0, 1)
                ${'$'}stream.Dispose()
                exit 0
            } catch { exit 73 }
            """.trimIndent(),
        )
        fun probe(): Int {
            val process = ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-NonInteractive",
                "-File",
                script.toString(),
                directory.resolve(".mihon-profile.lock").toString(),
            ).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
            assertTrue(process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS))
            return process.exitValue()
        }
        DesktopProfileLock.acquire(directory).use { assertEquals(73, probe()) }
        assertEquals(0, probe())
    }

    @Test
    fun `background update and backup use explicit headless commands`() {
        assertEquals(
            "BackgroundUpdate",
            DesktopCommandParser.parse(arrayOf("--background-update")).javaClass.simpleName,
        )
        assertEquals(
            "BackgroundBackup",
            DesktopCommandParser.parse(arrayOf("--background-backup")).javaClass.simpleName,
        )
    }
}
