package mihon.desktop.webview

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import mihon.desktop.extension.DesktopCookieStore
import mihon.desktop.extension.DesktopNetworkHelper
import mihon.extension.ipc.BrokerHttpRequest
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** A browser process is created only by open(), one capability and process per source session. */
class DesktopWebViewManager(
    private val browserRuntime: Path,
    private val hostDistribution: Path,
    private val cacheDirectory: Path,
    private val network: DesktopNetworkHelper,
    private val cookies: DesktopCookieStore,
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessions = ConcurrentHashMap<String, BrowserSession>()
    val activeSessionCount: Int get() = sessions.size
    private val extensionSessions = ConcurrentHashMap<String, BrowserSession>()
    suspend fun handleExtensionRequest(
        request: mihon.extension.ipc.WebViewRequest,
    ): mihon.extension.ipc.WebViewResponse {
        val key = "${request.extensionId}:${request.sourceId}:${request.sessionId}"
        return try {
            val value = when (request.action) {
                "open" -> {
                    extensionSessions.remove(key)?.close()
                    val session = open(request.sourceId, request.extensionId, request.url, request.headers)
                    extensionSessions[key] = session
                    try {
                        session.awaitLoaded()
                    } catch (
                        failure: Throwable,
                    ) {
                        extensionSessions.remove(key)?.close()
                        throw failure
                    }
                }
                "evaluate" -> (extensionSessions[key] ?: error("Browser session is not open")).evaluate(request.script)
                "destroy" -> {
                    extensionSessions.remove(key)?.close()
                    ""
                }
                "cookies", "getCookie" -> {
                    extensionSessions.entries.filter {
                        it.key.startsWith("${request.extensionId}:${request.sourceId}:")
                    }.forEach { it.value.exportCookies() }
                    if (request.url.isBlank()) {
                        ""
                    } else {
                        cookies.loadForRequest(
                            request.extensionId,
                            request.url.toHttpUrl(),
                        ).joinToString("; ") {
                            "${it.name}=${it.value}"
                        }
                    }
                }
                "setCookie" -> {
                    val url = request.url.toHttpUrl()
                    check(network.isDomainAllowed(url.host, request.extensionId)) { "Cookie origin is not allowed" }
                    val cookie = Cookie.parse(url, request.value) ?: error("Invalid browser cookie")
                    cookies.saveFromResponse(request.extensionId, url, listOf(cookie))
                    extensionSessions.entries.filter {
                        it.key.startsWith("${request.extensionId}:${request.sourceId}:")
                    }.forEach { it.value.setCookie(request.url, cookie) }
                    ""
                }
                "userAgent" -> network.userAgentFor(request.url.ifBlank { "https://localhost/" })
                else -> error("Unsupported WebView action: ${request.action}")
            }
            mihon.extension.ipc.WebViewResponse(true, value)
        } catch (
            cancelled: CancellationException,
        ) {
            throw cancelled
        } catch (failure: Exception) {
            mihon.extension.ipc.WebViewResponse(
                false,
                error =
                failure.message ?: "Browser operation failed",
            )
        }
    }

    suspend fun open(
        sourceId: Long,
        extensionId: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        timeoutMillis: Long = 30_000,
    ): BrowserSession {
        require(url.toHttpUrl().scheme in setOf("http", "https"))
        require(extensionId.isNotBlank())
        val release = browserRuntime.resolve("release")
        check(Files.isRegularFile(release) && Files.readString(release).contains("JAVA_VERSION=\"21.")) {
            "Independent Java 21 browser runtime is missing"
        }
        check(Files.isRegularFile(browserRuntime.resolve("bin/libcef.dll"))) {
            "Browser Chromium native runtime is missing"
        }
        check(Files.isDirectory(hostDistribution.resolve("lib"))) { "Browser host distribution is missing" }
        val id = UUID.randomUUID().toString()
        val token = ByteArray(32).also(SecureRandom()::nextBytes).let {
            Base64.getUrlEncoder().withoutPadding().encodeToString(it)
        }
        val cache = cacheDirectory.resolve(id)
        Files.createDirectories(cache)
        network.registerRuntimePageUrl(url, extensionId, sourceId)
        val session = BrowserSession(id, sourceId, extensionId, token, url, cache)
        sessions[id] = session
        return try {
            session.launch(mapOf("User-Agent" to network.userAgentFor(url)) + headers)
            withTimeout(timeoutMillis) { session.ready.await() }
            session
        } catch (failure: Throwable) {
            session.close()
            throw failure
        }
    }

    inner class BrowserSession internal constructor(
        val sessionId: String,
        val sourceId: Long,
        private val extensionId: String,
        private val token: String,
        private val initialUrl: String,
        private val cache: Path,
    ) : AutoCloseable {
        internal val ready = CompletableDeferred<Unit>()
        private val loaded = CompletableDeferred<String>()
        private val closed = AtomicBoolean(false)
        private val pending = ConcurrentHashMap<String, CompletableDeferred<String>>()
        private val updates = MutableSharedFlow<BrowserEvent>(extraBufferCapacity = 64)
        val events = updates.asSharedFlow()
        private val currentState = kotlinx.coroutines.flow.MutableStateFlow(BrowserEvent("starting", ""))
        val state: kotlinx.coroutines.flow.StateFlow<BrowserEvent> = currentState
        private val executor = Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "webview-broker-$sessionId").apply {
                isDaemon =
                    true
            }
        }
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        private var process: Process? = null
        private var processJob: mihon.desktop.extension.WindowsJobObject? = null
        private var writer: java.io.BufferedWriter? = null
        val processId: Long? get() = process?.pid()

        internal fun launch(headers: Map<String, String>) {
            server.executor = executor
            server.createContext("/request") { exchange ->
                try {
                    require(exchange.requestMethod == "POST")
                    val bytes = exchange.requestBody.readNBytes(8 * 1024 * 1024 + 1)
                    require(bytes.size <= 8 * 1024 * 1024)
                    val request = Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
                    require(
                        request.text("token") == token && request.text("sessionId") == sessionId &&
                            request.text("sourceId") == sourceId.toString(),
                    )
                    val response = runBlocking {
                        network.executeBrokeredRequest(
                            BrokerHttpRequest(
                                method = request.text("method"),
                                url = request.text("url"),
                                headers = (request["headers"] as? JsonObject).orEmpty().mapValues {
                                    it.value.jsonPrimitive.content
                                },
                                extensionId = extensionId,
                                sourceId = sourceId,
                                bodyBase64 = request.text("bodyBase64").takeIf(String::isNotBlank),
                            ),
                        )
                    }
                    val output = Json.encodeToString(response).toByteArray()
                    exchange.sendResponseHeaders(200, output.size.toLong())
                    exchange.responseBody.use { it.write(output) }
                } catch (
                    _: Exception,
                ) {
                    runCatching { exchange.sendResponseHeaders(403, -1) }
                } finally {
                    exchange.close()
                }
            }
            server.start()
            val command =
                listOf(
                    browserRuntime.resolve("bin/java.exe").toString(),
                    "--add-modules=jcef",
                    "--add-exports=java.desktop/sun.awt=ALL-UNNAMED",
                    "-cp",
                    (
                        hostDistribution.resolve("lib").toString() +
                            "/*"
                        ),
                    "mihon.webview.MainKt",
                )
            val child = ProcessBuilder(command).redirectError(cache.resolve("host-error.log").toFile()).start()
            process = child
            // The helper waits for stdin configuration, so no Chromium children can escape
            // before it is assigned to this kill-on-close process tree.
            val job = mihon.desktop.extension.WindowsJobObject(2L * 1024 * 1024 * 1024, activeProcessLimit = 32)
            processJob = job
            check(job.assignProcess(child)) { "Unable to contain browser process tree" }
            writer = child.outputStream.bufferedWriter(Charsets.UTF_8)
            val initialCookies = cookies.loadForRequest(extensionId, initialUrl.toHttpUrl())
            send(
                buildJsonObject {
                    put("url", initialUrl)
                    put("cache", cache.toAbsolutePath().toString())
                    put("broker", "http://127.0.0.1:${server.address.port}/request")
                    put("headers", buildJsonObject { headers.forEach { (key, value) -> put(key, value) } })
                    put(
                        "cookies",
                        buildJsonArray {
                            initialCookies.forEach { cookie ->
                                add(
                                    buildJsonObject {
                                        put("name", cookie.name)
                                        put("value", cookie.value)
                                        put("domain", cookie.domain)
                                        put("path", cookie.path)
                                        put("secure", cookie.secure)
                                        put("httpOnly", cookie.httpOnly)
                                    },
                                )
                            }
                        },
                    )
                },
            )
            scope.launch {
                child.waitFor()
                close()
            }
            scope.launch {
                try {
                    child.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                        lines.forEach { line ->
                            if (line.startsWith(
                                    "MIHON_WEBVIEW ",
                                )
                            ) {
                                onEvent(Json.parseToJsonElement(line.removePrefix("MIHON_WEBVIEW ")).jsonObject)
                            }
                        }
                    }
                } catch (failure: Exception) {
                    ready.completeExceptionally(failure)
                } finally {
                    val failure = IllegalStateException("Browser helper exited")
                    ready.completeExceptionally(failure)
                    loaded.completeExceptionally(failure)
                    pending.values.forEach { it.completeExceptionally(failure) }
                    close()
                }
            }
        }
        private fun onEvent(message: JsonObject) {
            if (message.text("sessionId") != sessionId || message.text("sourceId") != sourceId.toString()) return
            val type = message.text("type")
            val value = message.text("value")
            val id = message.text("id")
            when (type) {
                "ready" -> ready.complete(Unit)
                "loaded" -> if (value.startsWith("http")) loaded.complete(value)
                "result", "cookiesDone" -> pending.remove(id)?.complete(value)
                "cookie" -> importCookie(Json.parseToJsonElement(value).jsonObject)
                "error" -> {
                    val failure = IllegalStateException(value)
                    ready.completeExceptionally(failure)
                    loaded.completeExceptionally(failure)
                    pending.values.forEach { it.completeExceptionally(failure) }
                    close()
                }
                "closed" -> close()
            }
            val event = BrowserEvent(type, value)
            if (type in setOf("ready", "loaded", "error", "closed")) currentState.value = event
            updates.tryEmit(event)
        }
        private fun importCookie(value: JsonObject) {
            val domain = value.text("domain").removePrefix(".")
            if (!network.isDomainAllowed(domain, extensionId)) return
            val secure = value.text("secure") == "true"
            val builder = Cookie.Builder().name(value.text("name")).value(value.text("value")).path(
                value.text("path").ifBlank {
                    "/"
                },
            )
            if (value.text("domain").startsWith('.')) builder.domain(domain) else builder.hostOnlyDomain(domain)
            if (secure) builder.secure()
            if (value.text("httpOnly") == "true") builder.httpOnly()
            value.text("expiresAt").toLongOrNull()?.takeIf { it > 0 }?.let(builder::expiresAt)
            cookies.saveFromResponse(
                extensionId,
                "${if (secure) "https" else "http"}://$domain/".toHttpUrl(),
                listOf(builder.build()),
            )
        }
        suspend fun awaitLoaded(timeoutMillis: Long = 30_000): String = try {
            withTimeout(timeoutMillis) { loaded.await() }
        } catch (cancelled: CancellationException) {
            close()
            throw cancelled
        }
        suspend fun evaluate(
            script: String,
            timeoutMillis: Long = 10_000,
        ): String = query("evaluate", "script", script, timeoutMillis)
        internal suspend fun setCookie(url: String, cookie: Cookie) {
            val id = UUID.randomUUID().toString()
            val result = CompletableDeferred<String>()
            pending[id] = result
            try {
                send(
                    buildJsonObject {
                        put("type", "setCookie")
                        put("id", id)
                        put("url", url)
                        put(
                            "cookie",
                            buildJsonObject {
                                put("name", cookie.name)
                                put("value", cookie.value)
                                put("domain", if (cookie.hostOnly) cookie.domain else ".${cookie.domain}")
                                put("path", cookie.path)
                                put("secure", cookie.secure)
                                put("httpOnly", cookie.httpOnly)
                                put("expiresAt", if (cookie.persistent) cookie.expiresAt else 0)
                            },
                        )
                    },
                )
                withTimeout(5_000) { result.await() }
            } finally {
                pending.remove(id)
            }
        }
        suspend fun exportCookies(timeoutMillis: Long = 5_000) {
            query("cookies", "", "", timeoutMillis)
        }
        private suspend fun query(type: String, key: String, value: String, timeout: Long): String {
            val id = UUID.randomUUID().toString()
            val result = CompletableDeferred<String>()
            pending[id] = result
            return try {
                send(
                    buildJsonObject {
                        put("type", type)
                        put("id", id)
                        if (key.isNotEmpty()) put(key, value)
                    },
                )
                withTimeout(timeout) { result.await() }
            } catch (cancelled: CancellationException) {
                close()
                throw cancelled
            } finally {
                pending.remove(id)
            }
        }

        @Synchronized private fun send(message: JsonObject) {
            check(!closed.get()) { "Browser session closed" }
            val payload = JsonObject(
                message +
                    mapOf(
                        "sessionId" to JsonPrimitive(sessionId),
                        "sourceId" to JsonPrimitive(sourceId),
                        "token" to JsonPrimitive(token),
                    ),
            )
            writer?.apply {
                write(payload.toString())
                newLine()
                flush()
            } ?: error("Browser process has not started")
        }
        override fun close() {
            if (!closed.compareAndSet(false, true)) return
            currentState.value = BrowserEvent("closed", "")
            runCatching { writer?.close() }
            process?.let { child ->
                val descendants = child.descendants().toList()
                descendants.forEach { it.destroy() }
                child.destroy()
                if (!child.waitFor(2, TimeUnit.SECONDS)) child.destroyForcibly()
                descendants.filter { it.isAlive }.forEach { it.destroyForcibly() }
            }
            processJob?.close()
            processJob = null
            server.stop(0)
            executor.shutdownNow()
            sessions.remove(sessionId)
            extensionSessions.entries.removeIf { it.value === this }
            // The path was created under this manager's cache root for this UUID only.
            val root = cacheDirectory.toAbsolutePath().normalize()
            val owned = cache.toAbsolutePath().normalize()
            if (owned.startsWith(root) && owned != root) {
                runCatching {
                    Files.walk(owned).use { paths ->
                        paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
                    }
                }
            }
        }
    }
    override fun close() {
        sessions.values.toList().forEach { it.close() }
        scope.cancel()
    }
}
data class BrowserEvent(val type: String, val value: String)
private fun JsonObject.text(key: String): String = get(key)?.jsonPrimitive?.contentOrNull.orEmpty()
