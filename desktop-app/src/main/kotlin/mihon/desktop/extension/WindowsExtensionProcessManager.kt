package mihon.desktop.extension

import com.sun.jna.Platform
import kotlinx.coroutines.CancellationException
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
import mihon.extension.ipc.UnloadExtensionPayload
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
    private val networkHelper: DesktopNetworkHelper? = null,
    private val onWebView: (
        suspend (
            mihon.extension.ipc.WebViewRequest,
        ) -> mihon.extension.ipc.WebViewResponse
    )? = null,
    private val isolatedLeaf: Boolean = false,
) : Closeable {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val mutex = Mutex()
    private val routingMutex = Mutex()
    private val packageHosts = java.util.concurrent.ConcurrentHashMap<String, WindowsExtensionProcessManager>()
    private val packageFiles = java.util.concurrent.ConcurrentHashMap<String, File>()
    private val sourceHosts = java.util.concurrent.ConcurrentHashMap<Long, WindowsExtensionProcessManager>()
    private val routesPackages: Boolean get() = Platform.isWindows() && !isolatedLeaf

    private fun routedHost(sourceId: Long): WindowsExtensionProcessManager? = if (routesPackages) {
        sourceHosts[sourceId] ?: throw IpcException("No isolated host registered for source $sourceId")
    } else {
        null
    }
    var state: HostProcessState = HostProcessState.STOPPED
        private set

    var lastExitCode: Int? = null
        private set

    var epoch: Long = 0L
        private set

    private var process: Process? = null
    private var sandboxLauncher: WindowsAppContainerLauncher? = null
    private val hostWorkingDirectory: File
        get() = if (Platform.isWindows()) File(workingDirectory, "sandbox-data") else workingDirectory
    private var jobObject: WindowsJobObject? = null
    private var session: IpcSession? = null
    private val brokerPackages = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val brokerSources = java.util.concurrent.ConcurrentHashMap<Long, String>()

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

        hostWorkingDirectory.mkdirs()

        val command = customCommand ?: buildDefaultCommand()
        val stderrLog = File(hostWorkingDirectory, "extension-host-stderr.log")
        val proc = if (Platform.isWindows()) {
            // Session cookies and headers remain exclusively in the parent HTTP broker.
            val environment = emptyMap<String, String>()
            val launcher = WindowsAppContainerLauncher(hostWorkingDirectory, memoryLimitBytes)
            sandboxLauncher = launcher
            try {
                kotlinx.coroutines.withContext(Dispatchers.IO) { launcher.launch(command, environment) }
            } catch (
                cancelled: CancellationException,
            ) {
                launcher.close()
                sandboxLauncher = null
                state = HostProcessState.STOPPED
                throw cancelled
            } catch (error: Throwable) {
                sandboxLauncher = null
                state = HostProcessState.CRASHED
                throw IpcException("Isolated extension host launch failed: ${error.message}", error)
            }
        } else {
            ProcessBuilder(command).directory(hostWorkingDirectory).redirectError(stderrLog).apply {
                environment().remove("MIHON_SOURCE_SESSION_FILE")
                sourceSessionFile?.let { environment()["MIHON_SOURCE_SESSION_FILE"] = it.absolutePath }
            }.start()
        }
        this.process = proc
        val ipc = IpcSession(
            input = proc.inputStream,
            output = proc.outputStream,
            onCallback = { callback ->
                if (callback.callbackType == IpcCallbacks.BROKER_HTTP) {
                    val received = json.decodeFromString<BrokerHttpRequest>(callback.payloadJson)
                    // Detached extension coroutines/OkHttp dispatchers can lose thread-local identity.
                    // Only a dedicated, authenticated package process may supply the missing owner.
                    val request = received.copy(
                        extensionId = received.extensionId ?: if (isolatedLeaf) brokerPackages.singleOrNull() else null,
                    )
                    val permitted = networkHelper == null || (
                        request.extensionId != null && request.extensionId in brokerPackages &&
                            (request.sourceId == null || brokerSources[request.sourceId] == request.extensionId)
                        )
                    val response = if (!permitted) {
                        BrokerHttpResponse(403, error = "Unregistered extension network identity")
                    } else {
                        onBrokerHttp?.invoke(request) ?: BrokerHttpResponse(
                            statusCode = 503,
                            error = "Brokered HTTP handler not configured in process manager",
                        )
                    }
                    IpcCallbackResponse(
                        requestId = callback.requestId,
                        success = true,
                        payloadJson = json.encodeToString(prepareBrokerResponse(response)),
                    )
                } else if (callback.callbackType == "webview") {
                    val request = json.decodeFromString<mihon.extension.ipc.WebViewRequest>(callback.payloadJson)
                    val permitted =
                        request.extensionId in brokerPackages && brokerSources[request.sourceId] == request.extensionId
                    val response = if (!permitted) {
                        mihon.extension.ipc.WebViewResponse(false, error = "Unregistered extension WebView identity")
                    } else {
                        onWebView?.invoke(request)
                            ?: mihon.extension.ipc.WebViewResponse(false, error = "WebView handler is not configured")
                    }
                    IpcCallbackResponse(callback.requestId, success = true, payloadJson = json.encodeToString(response))
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
            if (process === proc && (state == HostProcessState.RUNNING || state == HostProcessState.STARTING)) {
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
        } catch (cancelled: CancellationException) {
            closeInternal()
            state = HostProcessState.STOPPED
            throw cancelled
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
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    }

    open suspend fun loadExtension(packageFile: File): List<SourceDescriptor> {
        if (routesPackages) {
            return routingMutex.withLock {
                val manifest = mihon.extension.validator.ExtensionPackageValidator.validatePackage(packageFile)
                packageHosts[manifest.id]?.let { existing ->
                    val current = existing.getSources()
                    val sources = if (current.isNotEmpty()) current else existing.loadExtension(packageFile)
                    registerRoutes(existing, sources)
                    return@withLock sources
                }
                val key = java.security.MessageDigest.getInstance("SHA-256").digest(manifest.id.toByteArray())
                    .joinToString("") { "%02x".format(it) }
                val child = WindowsExtensionProcessManager(
                    workingDirectory = File(workingDirectory, "isolated-extensions/$key"),
                    customCommand = customCommand,
                    memoryLimitBytes = memoryLimitBytes,
                    onBrokerHttp = onBrokerHttp,
                    sourceSessionFile = sourceSessionFile,
                    networkHelper = networkHelper,
                    onWebView = onWebView,
                    isolatedLeaf = true,
                )
                try {
                    val sources = child.loadExtension(packageFile)
                    registerRoutes(child, sources)
                    packageHosts[manifest.id] = child
                    packageFiles[manifest.id] = packageFile
                    sources
                } catch (failure: Throwable) {
                    child.close()
                    throw failure
                }
            }
        }
        val session = ensureRunningSession()
        val manifest = mihon.extension.validator.ExtensionPackageValidator.validatePackage(packageFile)
        brokerPackages.add(manifest.id)
        networkHelper?.registerExtensionDomains(manifest.id, ExtensionPackageDomains.resolve(packageFile, manifest))
        manifest.sources.forEach { source ->
            brokerSources[source.id] = manifest.id
            networkHelper?.registerSourceOwner(source.id, manifest.id)
        }
        val payload = LoadExtensionPayload(
            packagePath = if (Platform.isWindows()) {
                val packages = File(hostWorkingDirectory, "packages").apply { mkdirs() }
                packageFile.copyTo(File(packages, "${manifest.id}.wext"), overwrite = true).absolutePath
            } else {
                packageFile.absolutePath
            },
            workingDir = File(hostWorkingDirectory, manifest.id).absolutePath,
        )
        val res = session.sendRequest(IpcCommands.LOAD_EXTENSION, json.encodeToString(payload))
        return json.decodeFromString<List<SourceDescriptor>>(res).also { sources ->
            sources.forEach { source ->
                brokerSources[source.id] = manifest.id
                networkHelper?.registerSourceOwner(source.id, manifest.id)
            }
        }
    }

    private fun registerRoutes(host: WindowsExtensionProcessManager, sources: List<SourceDescriptor>) {
        check(sources.none { sourceHosts[it.id]?.let { owner -> owner !== host } == true }) {
            "Source identity is already owned by another extension host"
        }
        val ids = sources.mapTo(hashSetOf()) { it.id }
        sourceHosts.entries.removeIf { it.value === host && it.key !in ids }
        sources.forEach { sourceHosts[it.id] = host }
    }

    open suspend fun unloadExtension(pkg: String) {
        if (routesPackages) {
            routingMutex.withLock {
                packageFiles.remove(pkg)
                packageHosts.remove(pkg)?.let { child ->
                    sourceHosts.entries.removeIf { it.value === child }
                    try {
                        child.unloadExtension(pkg)
                    } finally {
                        child.close()
                    }
                }
            }
            return
        }
        if (state == HostProcessState.RUNNING) {
            ensureRunning().sendRequest(IpcCommands.UNLOAD_EXTENSION, json.encodeToString(UnloadExtensionPayload(pkg)))
        }
        brokerPackages.remove(pkg)
        brokerSources.entries.removeIf { it.value == pkg }
        networkHelper?.unregisterExtensionDomains(pkg)
    }

    private fun prepareBrokerResponse(response: BrokerHttpResponse): BrokerHttpResponse {
        val encoded = response.bodyBase64 ?: return response
        if (encoded.length <= 4 * 1024 * 1024) return response
        val bytes = java.util.Base64.getDecoder().decode(encoded)
        require(bytes.size <= 64 * 1024 * 1024) { "Broker response exceeds 64 MiB" }
        val directory = File(hostWorkingDirectory, "broker-responses").apply { mkdirs() }
        val file = File(directory, "broker-${java.util.UUID.randomUUID()}.bin")
        try {
            file.writeBytes(bytes)
        } catch (error: Exception) {
            file.delete()
            throw error
        }
        return response.copy(body = null, bodyBase64 = null, bodyFileName = file.name)
    }

    open suspend fun getSources(): List<SourceDescriptor> {
        if (routesPackages) {
            ensureRunningSession()
            return routingMutex.withLock {
                packageHosts.entries.flatMap { (pkg, child) ->
                    child.getSources().ifEmpty { child.loadExtension(packageFiles.getValue(pkg)) }
                        .also { registerRoutes(child, it) }
                }
            }
        }
        val session = ensureRunningSession()
        val res = session.sendRequest(IpcCommands.GET_SOURCES)
        return json.decodeFromString(res)
    }

    open suspend fun getPopular(sourceId: Long, page: Int): MangasPage {
        routedHost(sourceId)?.let { return it.getPopular(sourceId, page) }
        val session = ensureRunningSession()
        val payload = GetPagePayload(sourceId, page)
        val res = session.sendRequest(IpcCommands.GET_POPULAR, json.encodeToString(payload))
        return json.decodeFromString(res)
    }

    open suspend fun getLatest(sourceId: Long, page: Int): MangasPage {
        routedHost(sourceId)?.let { return it.getLatest(sourceId, page) }
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
        routedHost(sourceId)?.let { return it.searchManga(sourceId, page, query, filters) }
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
        routedHost(sourceId)?.let { return it.getMangaDetails(sourceId, manga) }
        val session = ensureRunningSession()
        val payload = MangaPayload(sourceId, json.encodeToString(manga))
        val res = session.sendRequest(IpcCommands.GET_MANGA_DETAILS, json.encodeToString(payload))
        return json.decodeFromString(res)
    }

    open suspend fun getChapterList(sourceId: Long, manga: SManga): List<SChapter> {
        routedHost(sourceId)?.let { return it.getChapterList(sourceId, manga) }
        val session = ensureRunningSession()
        val payload = MangaPayload(sourceId, json.encodeToString(manga))
        val res = session.sendRequest(IpcCommands.GET_CHAPTER_LIST, json.encodeToString(payload))
        return json.decodeFromString(res)
    }

    open suspend fun getPageList(sourceId: Long, chapter: SChapter): List<Page> {
        routedHost(sourceId)?.let { return it.getPageList(sourceId, chapter) }
        val session = ensureRunningSession()
        val payload = ChapterPayload(sourceId, json.encodeToString(chapter))
        val res = session.sendRequest(IpcCommands.GET_PAGE_LIST, json.encodeToString(payload))
        return json.decodeFromString(res)
    }

    open suspend fun getImage(sourceId: Long, page: Page): ByteArray? {
        routedHost(sourceId)?.let { return it.getImage(sourceId, page) }
        val ipc = ensureRunningSession()
        val response = ipc.sendRequest(
            IpcCommands.GET_IMAGE,
            json.encodeToString(ImagePayload(sourceId, page)),
            timeoutMillis = 120_000L,
        )
        val name = json.decodeFromString<ImageFilePayload>(response).fileName ?: return null
        require(name.matches(Regex("page-[a-zA-Z0-9-]+\\.img"))) { "Invalid image transfer filename" }
        val file = File(hostWorkingDirectory, "page-images/$name")
        try {
            require(file.length() <= 64L * 1024 * 1024) { "Source image exceeds 64 MiB" }
            return file.readBytes()
        } finally {
            file.delete()
        }
    }

    open suspend fun getFilterList(sourceId: Long): FilterList {
        routedHost(sourceId)?.let { return it.getFilterList(sourceId) }
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
        routedHost(sourceId)?.let { return it.getSourcePreferencesResult(sourceId) }
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
        routedHost(sourceId)?.let { return it.setSourcePreference(sourceId, key, value) }
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
        packageHosts.values.forEach { it.close() }
        packageHosts.clear()
        packageFiles.clear()
        sourceHosts.clear()
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
        sandboxLauncher?.close()
        sandboxLauncher = null
        brokerPackages.clear()
        brokerSources.clear()
        File(hostWorkingDirectory, "broker-responses").listFiles()?.filter {
            it.isFile && it.name.matches(Regex("broker-[a-zA-Z0-9-]+\\.bin"))
        }?.forEach { it.delete() }
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
