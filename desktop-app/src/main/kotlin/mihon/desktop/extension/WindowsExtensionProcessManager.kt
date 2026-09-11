package mihon.desktop.extension

import com.sun.jna.Platform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mihon.extension.ipc.BrokerHttpRequest
import mihon.extension.ipc.BrokerHttpResponse
import mihon.extension.ipc.ChapterPayload
import mihon.extension.ipc.GetPagePayload
import mihon.extension.ipc.ImageFilePayload
import mihon.extension.ipc.ImagePayload
import mihon.extension.ipc.IpcCallbackResponse
import mihon.extension.ipc.IpcCallbacks
import mihon.extension.ipc.IpcCommands
import mihon.extension.ipc.IpcException
import mihon.extension.ipc.IpcSession
import mihon.extension.ipc.LoadExtensionPayload
import mihon.extension.ipc.MangaPayload
import mihon.extension.ipc.SearchPayload
import mihon.extension.ipc.SetSourcePreferencePayload
import mihon.extension.ipc.SourcePayload
import mihon.extension.ipc.SourcePreferenceDefinitionDto
import mihon.extension.ipc.SourcePreferenceValueDto
import mihon.extension.ipc.SourcePreferencesDto
import mihon.extension.ipc.StringPreferenceValueDto
import mihon.extension.ipc.decodeFilterList
import mihon.extension.ipc.decodeSourcePreferenceValue
import mihon.extension.ipc.decodeSourcePreferences
import mihon.extension.ipc.encodeFilterList
import mihon.extension.model.SourceDescriptor
import mihon.extension.source.model.FilterList
import mihon.extension.source.model.MangasPage
import mihon.extension.source.model.Page
import mihon.extension.source.model.SChapter
import mihon.extension.source.model.SManga
import java.io.Closeable
import java.io.File

enum class HostProcessState {
    STOPPED,
    STARTING,
    RUNNING,
    CRASHED,
}

open class WindowsExtensionProcessManager(
    val workingDirectory: File,
    private val customCommand: List<String>? = null,
    private val memoryLimitBytes: Long = 1024L * 1024L * 1024L,
    private val onBrokerHttp: (suspend (BrokerHttpRequest) -> BrokerHttpResponse)? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job()),
    private val sourceSessionFile: File? = null,
) : Closeable {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val mutex = Mutex()
    var state: HostProcessState = HostProcessState.STOPPED
        private set

    var lastExitCode: Int? = null
        private set

    var epoch: Long = 0L
        private set

    private var process: Process? = null
    private var jobObject: WindowsJobObject? = null
    private var session: IpcSession? = null

    private var lastStderr: String = ""
        private set

    suspend fun start() = mutex.withLock {
        if (state == HostProcessState.RUNNING && process?.isAlive == true && session != null) return@withLock
        if (state == HostProcessState.RUNNING) {
            closeInternal()
            state = HostProcessState.CRASHED
        }
        if (state == HostProcessState.CRASHED) {
            closeInternal()
        }
        state = HostProcessState.STARTING
        lastExitCode = null
        lastStderr = ""

        workingDirectory.mkdirs()

        val command = customCommand ?: buildDefaultCommand()
        val stderrLog = File(workingDirectory, "extension-host-stderr.log")
        val pb = ProcessBuilder(command)
        pb.environment().remove("MIHON_SOURCE_SESSION_FILE")
        sourceSessionFile?.let { pb.environment()["MIHON_SOURCE_SESSION_FILE"] = it.absolutePath }
        pb.directory(workingDirectory)
        pb.redirectError(stderrLog)

        // Log launch command for debugging
        val cmdLog = File(workingDirectory, "extension-host-launch.log")
        cmdLog.writeText("Launch time: ${java.time.Instant.now()}\nCommand: ${command.joinToString(" ")}\n")

        val proc = pb.start()
        this.process = proc

        if (Platform.isWindows()) {
            val job = WindowsJobObject(memoryLimitBytes)
            job.assignProcess(proc)
            this.jobObject = job
        }

        val ipc = IpcSession(
            input = proc.inputStream,
            output = proc.outputStream,
            onCallback = { callback ->
                if (callback.callbackType == IpcCallbacks.BROKER_HTTP) {
                    val request = json.decodeFromString<BrokerHttpRequest>(callback.payloadJson)
                    val response = onBrokerHttp?.invoke(request) ?: BrokerHttpResponse(
                        statusCode = 503,
                        error = "Brokered HTTP handler not configured in process manager",
                    )
                    IpcCallbackResponse(
                        requestId = callback.requestId,
                        success = true,
                        payloadJson = json.encodeToString(response),
                    )
                } else {
                    IpcCallbackResponse(
                        requestId = callback.requestId,
                        success = false,
                        error = "Unknown callback '${callback.callbackType}'",
                    )
                }
            },
        )
        this.session = ipc

        scope.launch(Dispatchers.IO) {
            val code = proc.waitFor()
            lastExitCode = code
            lastStderr = try {
                stderrLog.readText().trim().take(8192)
            } catch (_: Exception) {
                ""
            }
            if (state == HostProcessState.RUNNING || state == HostProcessState.STARTING) {
                state = HostProcessState.CRASHED
                try {
                    ipc.close()
                } catch (_: Exception) {}
            }
        }

        try {
            val pong = ipc.sendRequest(IpcCommands.PING, timeoutMillis = 10_000L)
            if (pong != "pong") {
                throw IpcException("Unexpected ping response from extension host: $pong")
            }
            state = HostProcessState.RUNNING
            epoch++
        } catch (e: Exception) {
            closeInternal()
            // Give a moment for stderr to flush to file
            kotlinx.coroutines.delay(300)
            lastStderr = try {
                stderrLog.readText().trim().take(8192)
            } catch (_: Exception) {
                ""
            }
            state = HostProcessState.CRASHED
            val stderrInfo = if (lastStderr.isNotEmpty()) "\nStderr: $lastStderr" else ""
            throw IpcException("Failed to establish IPC handshake with extension host: ${e.message}$stderrInfo", e)
        }
    }

    private suspend fun ensureRunningSession(): IpcSession {
        if (state != HostProcessState.RUNNING) {
            start()
        }
        return ensureRunning()
    }

    private fun ensureRunning(): IpcSession {
        val proc = process
        if (proc != null && !proc.isAlive) {
            state = HostProcessState.CRASHED
            throw IpcException("Extension host process is dead (exitCode=${proc.exitValue()})")
        }
        return session ?: throw IpcException("Extension host is not running (state=$state)")
    }

    suspend fun ping(): Boolean {
        return try {
            val session = ensureRunningSession()
            session.sendRequest(IpcCommands.PING, timeoutMillis = 5_000L) == "pong"
        } catch (_: Exception) {
            false
        }
    }

    open suspend fun loadExtension(packageFile: File): List<SourceDescriptor> {
        val session = ensureRunningSession()
        val payload = LoadExtensionPayload(
            packagePath = packageFile.absolutePath,
            workingDir = File(workingDirectory, packageFile.nameWithoutExtension).absolutePath,
        )
        val res = session.sendRequest(IpcCommands.LOAD_EXTENSION, json.encodeToString(payload))
        return json.decodeFromString(res)
    }

    open suspend fun getSources(): List<SourceDescriptor> {
        val session = ensureRunningSession()
        val res = session.sendRequest(IpcCommands.GET_SOURCES)
        return json.decodeFromString(res)
    }

    open suspend fun getPopular(sourceId: Long, page: Int): MangasPage {
        val session = ensureRunningSession()
        val payload = GetPagePayload(sourceId, page)
        val res = session.sendRequest(IpcCommands.GET_POPULAR, json.encodeToString(payload))
        return json.decodeFromString(res)
    }

    open suspend fun getLatest(sourceId: Long, page: Int): MangasPage {
        val session = ensureRunningSession()
        val payload = GetPagePayload(sourceId, page)
        val res = session.sendRequest(IpcCommands.GET_LATEST, json.encodeToString(payload))
        return json.decodeFromString(res)
    }

    open suspend fun searchManga(sourceId: Long, page: Int, query: String): MangasPage {
        return searchManga(sourceId, page, query, FilterList())
    }

    open suspend fun searchManga(
        sourceId: Long,
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        val session = ensureRunningSession()
        val payload = SearchPayload(
            sourceId = sourceId,
            page = page,
            query = query,
            // Keep the legacy empty payload for the common "no filters" case.
            filtersJson = if (filters.isEmpty()) "" else encodeFilterList(filters),
        )
        val res = session.sendRequest(IpcCommands.SEARCH_MANGA, json.encodeToString(payload))
        return json.decodeFromString(res)
    }

    open suspend fun getMangaDetails(sourceId: Long, manga: SManga): SManga {
        val session = ensureRunningSession()
        val payload = MangaPayload(sourceId, json.encodeToString(manga))
        val res = session.sendRequest(IpcCommands.GET_MANGA_DETAILS, json.encodeToString(payload))
        return json.decodeFromString(res)
    }

    open suspend fun getChapterList(sourceId: Long, manga: SManga): List<SChapter> {
        val session = ensureRunningSession()
        val payload = MangaPayload(sourceId, json.encodeToString(manga))
        val res = session.sendRequest(IpcCommands.GET_CHAPTER_LIST, json.encodeToString(payload))
        return json.decodeFromString(res)
    }

    open suspend fun getPageList(sourceId: Long, chapter: SChapter): List<Page> {
        val session = ensureRunningSession()
        val payload = ChapterPayload(sourceId, json.encodeToString(chapter))
        val res = session.sendRequest(IpcCommands.GET_PAGE_LIST, json.encodeToString(payload))
        return json.decodeFromString(res)
    }

    open suspend fun getImage(sourceId: Long, page: Page): ByteArray? {
        val ipc = ensureRunningSession()
        val response = ipc.sendRequest(
            IpcCommands.GET_IMAGE,
            json.encodeToString(ImagePayload(sourceId, page)),
            timeoutMillis = 120_000L,
        )
        val name = json.decodeFromString<ImageFilePayload>(response).fileName ?: return null
        require(name.matches(Regex("page-[a-zA-Z0-9-]+\\.img"))) { "Invalid image transfer filename" }
        val file = File(workingDirectory, "page-images/$name")
        try {
            require(file.length() <= 64L * 1024 * 1024) { "Source image exceeds 64 MiB" }
            return file.readBytes()
        } finally {
            file.delete()
        }
    }

    open suspend fun getFilterList(sourceId: Long): FilterList {
        val session = ensureRunningSession()
        val payload = SourcePayload(sourceId)
        val res = session.sendRequest(IpcCommands.GET_FILTER_LIST, json.encodeToString(payload))
        return decodeFilterList(res)
    }

    /**
     * Full source preference response, including the `supported` flag that lets the desktop app
     * distinguish "source has no settings" from "source does not implement ConfigurableSource".
     */
    open suspend fun getSourcePreferencesResult(sourceId: Long): SourcePreferencesDto {
        val session = ensureRunningSession()
        val payload = SourcePayload(sourceId)
        val res = session.sendRequest(IpcCommands.GET_SOURCE_PREFERENCES, json.encodeToString(payload))
        return decodeSourcePreferences(res) ?: SourcePreferencesDto(sourceId = sourceId, supported = false)
    }

    /** Definitions (including current values) exposed by a source's ConfigurableSource. */
    open suspend fun getSourcePreferences(sourceId: Long): List<SourcePreferenceDefinitionDto> =
        getSourcePreferencesResult(sourceId).definitions

    /** Sets a typed source preference and returns the value reported by the host. */
    open suspend fun setSourcePreference(
        sourceId: Long,
        key: String,
        value: SourcePreferenceValueDto,
    ): SourcePreferenceValueDto? {
        val session = ensureRunningSession()
        val payload = SetSourcePreferencePayload(sourceId = sourceId, key = key, value = value)
        val res = session.sendRequest(IpcCommands.SET_SOURCE_PREFERENCE, json.encodeToString(payload))
        return decodeSourcePreferenceValue(res)
    }

    /** Convenience overload for callers that only have the raw UI string. */
    open suspend fun setSourcePreference(sourceId: Long, key: String, value: String) {
        setSourcePreference(sourceId, key, StringPreferenceValueDto(value))
    }

    suspend fun restart() {
        closeInternal()
        state = HostProcessState.STOPPED
        start()
    }

    private fun closeInternal() {
        val proc = process
        process = null

        try {
            proc?.destroyForcibly()
        } catch (_: Exception) {}

        try {
            jobObject?.terminate(0)
            jobObject?.close()
        } catch (_: Exception) {}
        jobObject = null

        try {
            proc?.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
        } catch (_: Exception) {}

        try {
            session?.close()
        } catch (_: Exception) {}
        session = null
    }

    override fun close() {
        closeInternal()
        state = HostProcessState.STOPPED
        scope.cancel()
    }

    private fun buildDefaultCommand(): List<String> {
        val currentCommand = ProcessHandle.current().info().command().orElse(null)
        val isPackagedExe = currentCommand != null &&
            !currentCommand.endsWith("java.exe", ignoreCase = true) &&
            !currentCommand.endsWith("javaw.exe", ignoreCase = true) &&
            !currentCommand.endsWith("java", ignoreCase = true)

        if (isPackagedExe) {
            return listOf(
                currentCommand,
                "--extension-host",
                "--stdio",
            )
        }

        val javaBin = currentCommand ?: run {
            val javaHome = System.getProperty("java.home")
            val isWindows = Platform.isWindows()
            val ext = if (isWindows) ".exe" else ""
            "$javaHome/bin/java$ext"
        }
        val locations = mutableSetOf<String>()
        fun addLocation(className: String) {
            try {
                val clazz = Class.forName(className)
                val uri = clazz.protectionDomain?.codeSource?.location?.toURI()
                if (uri != null) {
                    locations.add(File(uri).absolutePath)
                }
            } catch (_: Exception) {}
        }

        addLocation("mihon.extension.host.MainKt")
        addLocation("mihon.extension.source.WindowsSource")
        addLocation("kotlin.Unit")
        addLocation("kotlinx.coroutines.CoroutineScope")
        addLocation("kotlinx.serialization.json.Json")
        addLocation("org.jsoup.Jsoup")
        addLocation("okhttp3.OkHttpClient")
        addLocation("okio.ByteString")
        addLocation("uy.kohesive.injekt.Injekt")
        addLocation("rx.Observable")

        val sysCp = System.getProperty("java.class.path") ?: ""
        if (sysCp.isNotBlank()) {
            sysCp.split(File.pathSeparator).forEach {
                if (it.isNotBlank()) locations.add(File(it).absolutePath)
            }
        }

        val cp = locations.joinToString(File.pathSeparator)
        return listOf(
            javaBin,
            "-Xms16m",
            "-Xmx128m",
            "-XX:ReservedCodeCacheSize=32m",
            "-XX:MaxMetaspaceSize=64m",
            "-XX:+UseSerialGC",
            "-Dfile.encoding=UTF-8",
            "-cp",
            cp,
            "mihon.extension.host.MainKt",
            "--stdio",
        )
    }
}
