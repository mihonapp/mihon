package mihon.desktop.extension

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.WString
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinBase
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

internal class WindowsAppContainerLauncher(
    private val directory: File,
    private val memoryLimitBytes: Long,
    private val cpuRatePercent: Int = 50,
) : Closeable {
    private val native = WindowsSandboxNative
    private val profile = "MihonW.Extension.${UUID.randomUUID()}"
    private val sid = PointerByReference()
    private val grants = mutableListOf<File>()
    private var profileCreated = false
    private var pipe: WindowsAuthenticatedPipe? = null
    private var job: WindowsJobObject? = null
    private var process: SandboxProcess? = null
    private var sidText = ""
    private var runtimeLease: String? = null
    private var pendingLogHandle: com.sun.jna.platform.win32.WinNT.HANDLE? = null

    fun launch(command: List<String>, extraEnvironment: Map<String, String> = emptyMap()): Process {
        try {
            check(
                native.userenv.CreateAppContainerProfile(
                    WString(profile),
                    WString(profile),
                    WString("MihonW isolated extension host"),
                    null,
                    0,
                    sid,
                ) ==
                    0,
            ) {
                "Cannot create AppContainer profile; unisolated fallback is forbidden"
            }
            profileCreated = true
            val sidString = PointerByReference()
            native.requireSuccess(native.advapi.ConvertSidToStringSidW(sid.value, sidString), "ConvertSidToStringSid")
            sidText = sidString.value.getWideString(0)
            Kernel32.INSTANCE.LocalFree(sidString.value)
            directory.mkdirs()
            grant(directory, "(OI)(CI)(M)")
            runAcl(directory, "/setintegritylevel", "(OI)(CI)L")
            val executable = File(command.first()).canonicalFile
            require(executable.isFile) { "Extension host executable missing: $executable" }
            val runtime = if (executable.name.equals("java.exe", true) || executable.name.equals("javaw.exe", true)) {
                executable.parentFile.parentFile
            } else {
                executable.parentFile
            }
            // Installed runtimes may be administrator-owned. Copy into a parent-owned read-only
            // cache rather than altering Program Files ACLs or requesting elevation.
            val stagedRuntime = stageRuntime(runtime)
            val sandboxExecutable = stagedRuntime.resolve(executable.relativeTo(runtime))
            grant(stagedRuntime, "(OI)(CI)(RX)")
            command.indexOf("-cp").takeIf { it >= 0 }?.let { index ->
                command[index + 1].split(File.pathSeparator).filter { it.isNotBlank() }.map { File(it).canonicalFile }
                    .distinct().forEach { grant(it, if (it.isDirectory) "(OI)(CI)(RX)" else "(RX)") }
            }
            val objectPath = CharArray(1024)
            native.requireSuccess(
                native.kernel.GetAppContainerNamedObjectPath(
                    null,
                    sid.value,
                    objectPath.size,
                    objectPath,
                    IntByReference(),
                ),
                "GetAppContainerNamedObjectPath",
            )
            val sessionId = IntByReference()
            native.requireSuccess(
                native.kernel.ProcessIdToSessionId(ProcessHandle.current().pid().toInt(), sessionId),
                "ProcessIdToSessionId",
            )
            val namespace = "Sessions\\${sessionId.value}\\${Native.toString(objectPath)}"
            val channel = WindowsAuthenticatedPipe(sidText, namespace).also { pipe = it }
            val actual =
                (listOf(sandboxExecutable.absolutePath) + command.drop(1)).filterNot {
                    it == "--stdio" ||
                        it.startsWith("--pipe=")
                } +
                    listOf(
                        "--pipe-read=${channel.readName}",
                        "--pipe-write=${channel.writeName}",
                        "--stderr=${File(directory, "extension-host-stderr.log").absolutePath}",
                    )
            val size = Memory(Native.POINTER_SIZE.toLong()).apply { clear() }
            native.kernel.InitializeProcThreadAttributeList(null, 2, 0, size)
            val attributes = Memory(if (Native.POINTER_SIZE == 8) size.getLong(0) else size.getInt(0).toLong())
            native.requireSuccess(
                native.kernel.InitializeProcThreadAttributeList(attributes, 2, 0, size),
                "InitializeProcThreadAttributeList",
            )
            try {
                val capabilities = Memory((Native.POINTER_SIZE * 2 + 8).toLong()).apply {
                    clear()
                    setPointer(0, sid.value)
                }
                native.requireSuccess(
                    native.kernel.UpdateProcThreadAttribute(
                        attributes,
                        0,
                        0x20009,
                        capabilities,
                        capabilities.size(),
                        null,
                        null,
                    ),
                    "Security capabilities",
                )
                val inherit = WinBase.SECURITY_ATTRIBUTES().apply { bInheritHandle = true }
                val stderrHandle = Kernel32.INSTANCE.CreateFile(
                    File(directory, "extension-host-stderr.log").absolutePath,
                    0x40000000,
                    3,
                    inherit,
                    2,
                    0x80,
                    null,
                )
                native.requireSuccess(stderrHandle != WinBase.INVALID_HANDLE_VALUE, "Create sandbox stderr")
                pendingLogHandle = stderrHandle
                val handleList = Memory(Native.POINTER_SIZE.toLong()).apply { setPointer(0, stderrHandle.pointer) }
                native.requireSuccess(
                    native.kernel.UpdateProcThreadAttribute(
                        attributes,
                        0,
                        0x20002,
                        handleList,
                        handleList.size(),
                        null,
                        null,
                    ),
                    "Inherited log handle list",
                )
                val startupBase = WinBase.STARTUPINFO().apply {
                    cb = com.sun.jna.platform.win32.WinDef.DWORD((size() + Native.POINTER_SIZE).toLong())
                    dwFlags = 0x100
                    hStdError = stderrHandle
                    hStdOutput = stderrHandle
                    write()
                }
                val baseSize = startupBase.size()
                val startup = Memory((baseSize + Native.POINTER_SIZE).toLong()).apply {
                    clear()
                    write(0, startupBase.pointer.getByteArray(0, baseSize), 0, baseSize)
                    setPointer(baseSize.toLong(), attributes)
                }
                val envValues = sortedMapOf<String, String>(String.CASE_INSENSITIVE_ORDER)
                listOf("SystemRoot", "WINDIR", "SystemDrive").forEach { key ->
                    System.getenv(key)?.let {
                        envValues[key] =
                            it
                    }
                }
                envValues.putAll(extraEnvironment)
                envValues["TEMP"] = directory.absolutePath
                envValues["TMP"] = directory.absolutePath
                envValues["USERPROFILE"] = directory.absolutePath
                envValues["LOCALAPPDATA"] = directory.absolutePath
                envValues["APPDATA"] = directory.absolutePath
                envValues["MIHON_IPC_NONCE"] = channel.nonce
                val envText = envValues.entries.joinToString("\u0000") { "${it.key}=${it.value}" } + "\u0000\u0000"
                val envBytes = envText.toByteArray(Charsets.UTF_16LE)
                val environment = Memory(envBytes.size.toLong()).apply { write(0, envBytes, 0, envBytes.size) }
                val info = WinBase.PROCESS_INFORMATION()
                val cmd = (actual.joinToString(" ", transform = ::quoteWindowsArgument) + '\u0000').toCharArray()
                native.requireSuccess(
                    native.kernel.CreateProcessW(
                        WString(sandboxExecutable.absolutePath), cmd, null, null, true,
                        0x00080000 or 0x00000400 or 0x08000000 or 4, environment,
                        WString(
                            directory.absolutePath,
                        ),
                        startup, info,
                    ),
                    "CreateProcess AppContainer",
                )
                Kernel32.INSTANCE.CloseHandle(stderrHandle)
                pendingLogHandle = null
                val proc = SandboxProcess(info, channel).also { process = it }
                try {
                    check(native.tokenIsAppContainer(info.hProcess)) { "Windows did not issue an AppContainer token" }
                    val processJob = WindowsJobObject(memoryLimitBytes, cpuRatePercent = cpuRatePercent).also {
                        job = it
                    }
                    check(processJob.assignProcess(proc)) {
                        "Cannot assign suspended AppContainer to resource Job Object"
                    }
                    channel.prepare()
                    check(native.kernel.ResumeThread(info.hThread) != -1) { "Cannot resume sandbox process" }
                } finally {
                    Kernel32.INSTANCE.CloseHandle(info.hThread)
                }
                try {
                    channel.authenticate(proc.pid())
                } catch (failure: Exception) {
                    error(
                        "Sandbox pipe handshake failed (alive=${proc.isAlive}, exit=${runCatching {
                            proc.exitValue()
                        }.getOrNull()}): ${File(directory, "extension-host-stderr.log").takeIf {
                            it.isFile
                        }?.readText()?.take(4096)}; $failure",
                    )
                }
                return proc
            } finally {
                native.kernel.DeleteProcThreadAttributeList(attributes)
            }
        } catch (failure: Throwable) {
            close()
            throw failure
        }
    }

    private fun stageRuntime(original: File): File = synchronized(runtimeCaches) {
        runtimeCaches[original.absolutePath]?.takeIf { it.isDirectory }?.let {
            runtimeUsers[original.absolutePath] = runtimeUsers.getValue(original.absolutePath) + 1
            runtimeLease = original.absolutePath
            return@synchronized it
        }
        val target = java.nio.file.Files.createTempDirectory("mihonw-sandbox-runtime-").toFile()
        original.walkTopDown().forEach { source ->
            val destination = target.resolve(source.relativeTo(original))
            if (source.isDirectory) destination.mkdirs() else source.copyTo(destination)
        }
        runtimeCaches[original.absolutePath] = target
        runtimeUsers[original.absolutePath] = 1
        runtimeLease = original.absolutePath
        target
    }

    companion object {
        private val runtimeCaches = mutableMapOf<String, File>()
        private val runtimeUsers = mutableMapOf<String, Int>()
    }

    private fun grant(path: File, rights: String) {
        require(path.exists()) { "Missing sandbox read resource: $path" }
        runAcl(path, "/grant", "*$sidText:$rights")
        grants += path
    }
    private fun runAcl(path: File, vararg arguments: String) {
        val executable = File(System.getenv("SystemRoot"), "System32/icacls.exe")
        val process = ProcessBuilder(
            listOf(executable.absolutePath, path.absolutePath) + arguments,
        ).redirectErrorStream(true).start()
        val result = process.inputStream.bufferedReader().readText()
        check(process.waitFor() == 0) { "Sandbox ACL setup failed for $path: $result" }
    }
    override fun close() {
        pendingLogHandle?.let { Kernel32.INSTANCE.CloseHandle(it) }
        pendingLogHandle = null
        job?.terminate(1)
        process?.destroyForcibly()
        process?.waitFor(5, TimeUnit.SECONDS)
        pipe?.close()
        pipe = null
        job?.close()
        job = null
        process?.closeHandle()
        process = null
        if (sidText.isNotEmpty()) {
            grants.asReversed().forEach { runCatching { runAcl(it, "/remove:g", "*$sidText") } }
            grants.clear()
        }
        if (profileCreated) {
            native.userenv.DeleteAppContainerProfile(WString(profile))
            profileCreated = false
        }
        sid.value?.let {
            native.advapi.FreeSid(it)
            sid.value = null
        }
        synchronized(runtimeCaches) {
            runtimeLease?.let { key ->
                val remaining = runtimeUsers.getValue(key) - 1
                if (remaining == 0) {
                    runtimeUsers.remove(key)
                    runtimeCaches.remove(key)?.let { owned ->
                        java.nio.file.Files.walk(owned.toPath()).use { paths ->
                            paths.sorted(Comparator.reverseOrder()).forEach { java.nio.file.Files.deleteIfExists(it) }
                        }
                    }
                } else {
                    runtimeUsers[key] = remaining
                }
            }
            runtimeLease = null
        }
    }
}

internal fun quoteWindowsArgument(argument: String): String {
    val escaped = Regex("(\\\\*)\"").replace(argument) { "\\".repeat(it.groupValues[1].length * 2 + 1) + "\"" }
    val trailing = escaped.takeLastWhile { it == '\\' }.length
    return "\"$escaped${"\\".repeat(trailing)}\""
}

internal class SandboxProcess(
    private val info: WinBase.PROCESS_INFORMATION,
    private val channel: WindowsAuthenticatedPipe,
) : Process() {
    @Volatile private var cachedExitCode: Int? = null
    override fun getInputStream(): InputStream = channel.input
    override fun getOutputStream(): OutputStream = channel.output
    override fun getErrorStream(): InputStream = InputStream.nullInputStream()
    override fun pid(): Long = info.dwProcessId.toLong()
    override fun waitFor(): Int {
        Kernel32.INSTANCE.WaitForSingleObject(info.hProcess, -1)
        return exitValue()
    }
    override fun waitFor(timeout: Long, unit: TimeUnit): Boolean =
        Kernel32.INSTANCE.WaitForSingleObject(
            info.hProcess,
            unit.toMillis(timeout).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        ) ==
            0

    @Synchronized override fun exitValue(): Int {
        cachedExitCode?.let { return it }
        val code = IntByReference()
        WindowsSandboxNative.requireSuccess(
            Kernel32.INSTANCE.GetExitCodeProcess(info.hProcess, code),
            "GetExitCodeProcess",
        )
        if (code.value == 259) throw IllegalThreadStateException("Sandbox process is running")
        cachedExitCode = code.value
        return code.value
    }
    override fun isAlive(): Boolean = !waitFor(0, TimeUnit.MILLISECONDS)
    override fun destroy() {
        Kernel32.INSTANCE.TerminateProcess(info.hProcess, 1)
    }
    override fun destroyForcibly(): Process {
        destroy()
        return this
    }

    @Synchronized fun closeHandle() {
        if (cachedExitCode == null) cachedExitCode = runCatching { exitValue() }.getOrDefault(1)
        Kernel32.INSTANCE.CloseHandle(info.hProcess)
    }
}
