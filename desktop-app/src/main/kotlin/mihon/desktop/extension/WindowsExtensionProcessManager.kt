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
import mihon.extension.ipc.IpcCallbackResponse
import mihon.extension.ipc.IpcCallbacks
import mihon.extension.ipc.IpcCommands
import mihon.extension.ipc.IpcException
import mihon.extension.ipc.IpcSession
import mihon.extension.ipc.LoadExtensionPayload
import mihon.extension.ipc.MangaPayload
import mihon.extension.ipc.SearchPayload
import mihon.extension.ipc.SourcePayload
import mihon.extension.model.SourceDescriptor
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

class WindowsExtensionProcessManager(
    val workingDirectory: File,
    private val customCommand: List<String>? = null,
    private val memoryLimitBytes: Long = 1024L * 1024L * 1024L,
    private val onBrokerHttp: (suspend (BrokerHttpRequest) -> BrokerHttpResponse)? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job()),
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

    private var process: Process? = null
    private var jobObject: WindowsJobObject? = null
    private var session: IpcSession? = null

    suspend fun start() = mutex.withLock {
        if (state == HostProcessState.RUNNING) return@withLock
        state = HostProcessState.STARTING
        lastExitCode = null

        workingDirectory.mkdirs()

        val command = customCommand ?: buildDefaultCommand()
        val pb = ProcessBuilder(command)
        pb.directory(workingDirectory)
        pb.redirectError(ProcessBuilder.Redirect.INHERIT)

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
        } catch (e: Exception) {
            closeInternal()
            state = HostProcessState.CRASHED
            throw IpcException("Failed to establish IPC handshake with extension host: ${e.message}", e)
        }
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
            val session = ensureRunning()
            session.sendRequest(IpcCommands.PING, timeoutMillis = 5_000L) == "pong"
        } catch (_: Exception) {
            false
        }
    }

    suspend fun loadExtension(packageFile: File): List<SourceDescriptor> {
        val session = ensureRunning()
        val payload = LoadExtensionPayload(
            packagePath = packageFile.absolutePath,
            workingDir = File(workingDirectory, packageFile.nameWithoutExtension).absolutePath,
        )
        val res = session.sendRequest(IpcCommands.LOAD_EXTENSION, json.encodeToString(payload))
        return json.decodeFromString(res)
    }

    suspend fun getSources(): List<SourceDescriptor> {
        val session = ensureRunning()
        val res = session.sendRequest(IpcCommands.GET_SOURCES)
        return json.decodeFromString(res)
    }

    suspend fun getPopular(sourceId: Long, page: Int): MangasPage {
        val session = ensureRunning()
        val payload = GetPagePayload(sourceId, page)
        val res = session.sendRequest(IpcCommands.GET_POPULAR, json.encodeToString(payload))
        return json.decodeFromString(res)
    }

    suspend fun getLatest(sourceId: Long, page: Int): MangasPage {
        val session = ensureRunning()
        val payload = GetPagePayload(sourceId, page)
        val res = session.sendRequest(IpcCommands.GET_LATEST, json.encodeToString(payload))
        return json.decodeFromString(res)
    }

    suspend fun searchManga(sourceId: Long, page: Int, query: String): MangasPage {
        val session = ensureRunning()
        val payload = SearchPayload(sourceId, page, query)
        val res = session.sendRequest(IpcCommands.SEARCH_MANGA, json.encodeToString(payload))
        return json.decodeFromString(res)
    }

    suspend fun getMangaDetails(sourceId: Long, manga: SManga): SManga {
        val session = ensureRunning()
        val payload = MangaPayload(sourceId, json.encodeToString(manga))
        val res = session.sendRequest(IpcCommands.GET_MANGA_DETAILS, json.encodeToString(payload))
        return json.decodeFromString(res)
    }

    suspend fun getChapterList(sourceId: Long, manga: SManga): List<SChapter> {
        val session = ensureRunning()
        val payload = MangaPayload(sourceId, json.encodeToString(manga))
        val res = session.sendRequest(IpcCommands.GET_CHAPTER_LIST, json.encodeToString(payload))
        return json.decodeFromString(res)
    }

    suspend fun getPageList(sourceId: Long, chapter: SChapter): List<Page> {
        val session = ensureRunning()
        val payload = ChapterPayload(sourceId, json.encodeToString(chapter))
        val res = session.sendRequest(IpcCommands.GET_PAGE_LIST, json.encodeToString(payload))
        return json.decodeFromString(res)
    }

    suspend fun getFilterList(sourceId: Long): List<String> {
        val session = ensureRunning()
        val payload = SourcePayload(sourceId)
        val res = session.sendRequest(IpcCommands.GET_FILTER_LIST, json.encodeToString(payload))
        return json.decodeFromString(res)
    }

    suspend fun restart() {
        close()
        start()
    }

    private fun closeInternal() {
        val proc = process
        process = null

        try {
            proc?.destroyForcibly()
            proc?.waitFor(1000, java.util.concurrent.TimeUnit.MILLISECONDS)
        } catch (_: Exception) {}

        try {
            jobObject?.terminate(0)
            jobObject?.close()
        } catch (_: Exception) {}
        jobObject = null

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
        val javaBin = ProcessHandle.current().info().command().orElseGet {
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
