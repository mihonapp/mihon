package eu.kanade.tachiyomi.ui.reader.cast

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import logcat.LogPriority
import org.json.JSONArray
import org.json.JSONObject
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

/**
 * Minimal dependency-free HTTP/1.1 server backing the web cast target.
 *
 * Serves `assets/cast/receiver.html` and the JSON/image API it consumes:
 * - `GET /api/state?v=N` long-polls until the cast state is newer than `N`.
 * - `GET /img/{chapterId}/{index}` returns the encoded page image.
 * - `GET /api/cmd?do=name` forwards a remote command to the [CastController].
 *
 * A server instance is single-use: once [stop] has been called it cannot be started again.
 */
class CastWebServer(
    private val context: Context,
    private val controller: CastController,
    private val port: Int,
    private val quality: Preference<CastImageQuality>,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Guards long-poll waiters; signalled whenever the controller publishes a new state.
    private val stateLock = ReentrantLock()
    private val stateChanged = stateLock.newCondition()

    @Volatile
    private var running = false

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var executor: ExecutorService? = null

    private val connections: MutableSet<Socket> = Collections.newSetFromMap(ConcurrentHashMap())
    private val threadCounter = AtomicInteger()

    // Remote IP -> last time it polled the state (elapsedRealtime). Guarded by itself.
    private val clients = HashMap<String, Long>()
    private var reportedClientCount = 0

    private val receiverPage: ByteArray? by lazy {
        try {
            context.assets.open(RECEIVER_ASSET).use { it.readBytes() }
        } catch (e: IOException) {
            logcat(LogPriority.ERROR, e) { "Unable to read the cast receiver page" }
            null
        }
    }

    @Throws(IOException::class)
    fun start(): CastWebInfo {
        check(!running) { "CastWebServer is already running" }
        val address = findLanAddress()
            ?: throw IOException(context.stringResource(MR.strings.cast_web_no_network))
        val socket = bind()
        serverSocket = socket
        running = true
        executor = Executors.newFixedThreadPool(MAX_CONNECTIONS) { runnable ->
            Thread(runnable, "CastWebServer-${threadCounter.incrementAndGet()}").apply { isDaemon = true }
        }

        controller.state
            .onEach { stateLock.withLock { stateChanged.signalAll() } }
            .launchIn(scope)
        scope.launch {
            while (isActive) {
                delay(CLIENT_REFRESH_MS)
                refreshClients()
            }
        }
        thread(name = "CastWebServer", isDaemon = true) { acceptLoop(socket) }

        val info = CastWebInfo(
            url = "http://${address.hostAddress}:${socket.localPort}",
            port = socket.localPort,
            clientCount = 0,
        )
        logcat { "Cast web server listening on ${info.url}" }
        return info
    }

    fun stop() {
        running = false
        try {
            scope.cancel()
            serverSocket?.let { socket ->
                serverSocket = null
                runCatching { socket.close() }
            }
            stateLock.withLock { stateChanged.signalAll() }
            executor?.shutdownNow()
            executor = null
            connections.forEach { runCatching { it.close() } }
            connections.clear()
            synchronized(clients) {
                clients.clear()
                reportedClientCount = 0
            }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "Error while stopping the cast web server" }
        }
    }

    // region Networking

    private fun findLanAddress(): Inet4Address? {
        val interfaces = try {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        } catch (e: SocketException) {
            logcat(LogPriority.ERROR, e) { "Unable to list network interfaces" }
            return null
        }
        return interfaces
            .filter { iface ->
                try {
                    !iface.isLoopback && iface.isUp
                } catch (e: SocketException) {
                    false
                }
            }
            .sortedBy { iface -> interfacePriority(iface.name.orEmpty()) }
            .firstNotNullOfOrNull { iface ->
                iface.inetAddresses.toList()
                    .filterIsInstance<Inet4Address>()
                    .firstOrNull { it.isSiteLocalAddress && !it.isLoopbackAddress }
            }
    }

    private fun interfacePriority(name: String): Int = when {
        name.startsWith("wlan") -> 0
        name.startsWith("ap") -> 1
        name.startsWith("eth") -> 2
        else -> 3
    }

    @Throws(IOException::class)
    private fun bind(): ServerSocket {
        var lastError: IOException? = null
        for (candidate in port..(port + PORT_ATTEMPTS)) {
            if (candidate !in 1..MAX_PORT) break
            val socket = ServerSocket()
            try {
                socket.reuseAddress = true
                socket.bind(InetSocketAddress(candidate), ACCEPT_BACKLOG)
                return socket
            } catch (e: IOException) {
                runCatching { socket.close() }
                lastError = e
            }
        }
        throw lastError ?: IOException("No free port found from $port")
    }

    private fun acceptLoop(socket: ServerSocket) {
        while (running && !socket.isClosed) {
            val client = try {
                socket.accept()
            } catch (e: IOException) {
                if (!running || socket.isClosed) break
                logcat(LogPriority.ERROR, e) { "Cast web server failed to accept a connection" }
                continue
            }
            connections += client
            val pool = executor
            try {
                if (pool == null || pool.isShutdown) throw RejectedExecutionException("Server stopped")
                pool.execute { handleConnection(client) }
            } catch (e: RejectedExecutionException) {
                connections -= client
                runCatching { client.close() }
            }
        }
    }

    private fun handleConnection(socket: Socket) {
        val remoteIp = socket.inetAddress?.hostAddress ?: "?"
        try {
            socket.soTimeout = SOCKET_TIMEOUT_MS
            socket.tcpNoDelay = true
            val input = BufferedInputStream(socket.getInputStream(), INPUT_BUFFER_SIZE)
            val output = BufferedOutputStream(socket.getOutputStream(), OUTPUT_BUFFER_SIZE)
            while (running && !socket.isClosed) {
                val request = try {
                    readRequest(input) ?: break
                } catch (e: BadRequestException) {
                    logcat { "Rejecting cast web request from $remoteIp: ${e.message}" }
                    writeResponse(output, "GET", Response.text(STATUS_BAD_REQUEST, "Bad request"), close = true)
                    break
                }
                val response = try {
                    route(request, remoteIp)
                } catch (e: InterruptedException) {
                    throw e
                } catch (e: Throwable) {
                    logcat(LogPriority.ERROR, e) { "Cast web request failed: ${request.method} ${request.path}" }
                    Response.text(STATUS_INTERNAL_ERROR, "Internal error")
                }
                val close = !request.keepAlive || response.status >= STATUS_BAD_REQUEST || !running
                writeResponse(output, request.method, response, close)
                if (close) break
            }
        } catch (e: SocketTimeoutException) {
            // Idle keep-alive connection; the client will reconnect when it needs to.
        } catch (e: SocketException) {
            // Expected while stopping (socket closed) or when the client goes away.
            if (running) logcat(LogPriority.DEBUG, e) { "Cast web connection to $remoteIp closed" }
        } catch (e: IOException) {
            logcat(LogPriority.DEBUG, e) { "Cast web connection to $remoteIp failed" }
        } catch (e: InterruptedException) {
            // The executor is shutting down.
            Thread.currentThread().interrupt()
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "Cast web connection handler crashed" }
        } finally {
            connections -= socket
            runCatching { socket.close() }
        }
    }

    // endregion

    // region HTTP parsing / writing

    private class BadRequestException(message: String) : Exception(message)

    private class Request(
        val method: String,
        val path: String,
        val query: Map<String, String>,
        val keepAlive: Boolean,
    )

    private class Response(
        val status: Int,
        val contentType: String,
        val body: ByteArray,
        val cacheControl: String = "no-cache",
    ) {
        companion object {
            fun text(status: Int, text: String) = Response(status, "text/plain; charset=utf-8", text.toByteArray())

            fun json(status: Int, json: String) = Response(status, JSON_CONTENT_TYPE, json.toByteArray())
        }
    }

    /** Returns null when the client closed the connection before sending a request. */
    @Throws(IOException::class, BadRequestException::class)
    private fun readRequest(input: InputStream): Request? {
        var line = readLine(input) ?: return null
        // Tolerate a few stray empty lines between keep-alive requests (RFC 7230 §3.5).
        var skipped = 0
        while (line.isEmpty() && skipped++ < MAX_EMPTY_LINES) {
            line = readLine(input) ?: return null
        }
        val parts = line.split(' ')
        if (parts.size != 3 || parts[1].isEmpty() || !parts[2].startsWith("HTTP/")) {
            throw BadRequestException("Malformed request line")
        }
        val method = parts[0]
        val http11 = parts[2] != "HTTP/1.0"

        val headers = HashMap<String, String>()
        var headersLength = 0
        while (true) {
            val header = readLine(input) ?: throw BadRequestException("Unexpected end of headers")
            if (header.isEmpty()) break
            headersLength += header.length
            if (headersLength > MAX_HEADERS_LENGTH) throw BadRequestException("Headers too long")
            val colon = header.indexOf(':')
            if (colon <= 0) throw BadRequestException("Malformed header")
            headers[header.substring(0, colon).trim().lowercase()] = header.substring(colon + 1).trim()
        }
        val connection = headers["connection"].orEmpty().lowercase()
        val keepAlive = if (http11) "close" !in connection else "keep-alive" in connection

        var target = parts[1]
        if (target.startsWith("http://", ignoreCase = true) || target.startsWith("https://", ignoreCase = true)) {
            val slash = target.indexOf('/', target.indexOf("://") + 3)
            target = if (slash >= 0) target.substring(slash) else "/"
        }
        if (!target.startsWith('/')) throw BadRequestException("Unsupported request target")
        val question = target.indexOf('?')
        val rawPath = if (question >= 0) target.substring(0, question) else target
        val rawQuery = if (question >= 0) target.substring(question + 1) else ""
        return Request(method, percentDecode(rawPath), parseQuery(rawQuery), keepAlive)
    }

    /** Reads a CRLF (or LF) terminated line as ISO-8859-1; null at end of stream. */
    @Throws(IOException::class, BadRequestException::class)
    private fun readLine(input: InputStream): String? {
        val buffer = ByteArrayOutputStream(64)
        while (true) {
            val b = input.read()
            if (b == -1) {
                if (buffer.size() == 0) return null
                break
            }
            if (b == '\n'.code) break
            if (b != '\r'.code) buffer.write(b)
            if (buffer.size() > MAX_LINE_LENGTH) throw BadRequestException("Line too long")
        }
        return String(buffer.toByteArray(), Charsets.ISO_8859_1)
    }

    private fun parseQuery(rawQuery: String): Map<String, String> {
        if (rawQuery.isEmpty()) return emptyMap()
        val query = HashMap<String, String>()
        rawQuery.split('&').forEach { pair ->
            if (pair.isEmpty()) return@forEach
            val eq = pair.indexOf('=')
            val key = if (eq >= 0) pair.substring(0, eq) else pair
            val value = if (eq >= 0) pair.substring(eq + 1) else ""
            query[percentDecode(key.replace('+', ' '))] = percentDecode(value.replace('+', ' '))
        }
        return query
    }

    // Lines are read as ISO-8859-1 so every char is exactly one original byte.
    private fun percentDecode(value: String): String {
        if (value.indexOf('%') < 0) return value
        val out = ByteArrayOutputStream(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '%' && i + 2 < value.length) {
                val byte = value.substring(i + 1, i + 3).toIntOrNull(16)
                if (byte != null) {
                    out.write(byte)
                    i += 3
                    continue
                }
            }
            out.write(c.code and 0xFF)
            i++
        }
        return String(out.toByteArray(), Charsets.UTF_8)
    }

    @Throws(IOException::class)
    private fun writeResponse(output: OutputStream, method: String, response: Response, close: Boolean) {
        val head = StringBuilder(256)
            .append("HTTP/1.1 ").append(response.status).append(' ').append(statusText(response.status)).append(CRLF)
            .append("Content-Type: ").append(response.contentType).append(CRLF)
            .append("Content-Length: ").append(response.body.size).append(CRLF)
            .append("Cache-Control: ").append(response.cacheControl).append(CRLF)
            .append("Connection: ").append(if (close) "close" else "keep-alive").append(CRLF)
        if (response.status == STATUS_METHOD_NOT_ALLOWED) head.append("Allow: GET, HEAD").append(CRLF)
        head.append(CRLF)
        output.write(head.toString().toByteArray(Charsets.ISO_8859_1))
        if (method != "HEAD") output.write(response.body)
        output.flush()
    }

    private fun statusText(status: Int): String = when (status) {
        STATUS_OK -> "OK"
        STATUS_BAD_REQUEST -> "Bad Request"
        STATUS_NOT_FOUND -> "Not Found"
        STATUS_METHOD_NOT_ALLOWED -> "Method Not Allowed"
        STATUS_INTERNAL_ERROR -> "Internal Server Error"
        STATUS_UNAVAILABLE -> "Service Unavailable"
        else -> "Unknown"
    }

    // endregion

    // region Routes

    private fun route(request: Request, remoteIp: String): Response {
        if (request.method != "GET" && request.method != "HEAD") {
            return Response.text(STATUS_METHOD_NOT_ALLOWED, "Method not allowed")
        }
        val path = request.path
        return when {
            path == "/" || path == "/index.html" -> receiverResponse()
            path == "/api/state" -> stateResponse(request, remoteIp)
            path == "/api/cmd" -> commandResponse(request)
            path.startsWith("/img/") -> imageResponse(path)
            else -> Response.text(STATUS_NOT_FOUND, "Not found")
        }
    }

    private fun receiverResponse(): Response {
        val page = receiverPage ?: return Response.text(STATUS_INTERNAL_ERROR, "Receiver page missing")
        return Response(STATUS_OK, "text/html; charset=utf-8", page)
    }

    private fun stateResponse(request: Request, remoteIp: String): Response {
        touchClient(remoteIp)
        val known = request.query["v"]?.toLongOrNull() ?: 0L
        val state = awaitNewerState(known)
        refreshClients()
        return Response.json(STATUS_OK, state.toJson())
    }

    private fun commandResponse(request: Request): Response {
        val command = request.query["do"].orEmpty()
        val ok = command.isNotEmpty() && controller.remoteCommand(command)
        return if (ok) {
            Response.json(STATUS_OK, """{"ok":true}""")
        } else {
            Response.json(STATUS_BAD_REQUEST, """{"ok":false}""")
        }
    }

    private fun imageResponse(path: String): Response {
        val notFound = Response.text(STATUS_NOT_FOUND, "Not found")
        val parts = path.removePrefix("/img/").split('/')
        if (parts.size != 2) return notFound
        val chapterId = parts[0].toLongOrNull() ?: return notFound
        val index = parts[1].toIntOrNull()?.takeIf { it >= 0 } ?: return notFound
        val image = runBlocking {
            withTimeoutOrNull(IMAGE_TIMEOUT_MS) {
                controller.images.encoded(CastPageInfo(chapterId, index), quality.get())
            }
        } ?: return Response.text(STATUS_UNAVAILABLE, "Image unavailable")
        return Response(STATUS_OK, image.mimeType, image.bytes, cacheControl = "max-age=3600")
    }

    /**
     * Blocks until the controller publishes a version newer than [known] or [LONG_POLL_TIMEOUT_MS]
     * elapses. A client that is ahead of us (stale version from a previous process) is answered
     * right away so it resyncs.
     */
    private fun awaitNewerState(known: Long): CastState {
        val deadline = SystemClock.elapsedRealtime() + LONG_POLL_TIMEOUT_MS
        stateLock.withLock {
            while (running) {
                val state = controller.state.value
                if (state.version != known) return state
                val remaining = deadline - SystemClock.elapsedRealtime()
                if (remaining <= 0) return state
                stateChanged.await(remaining, TimeUnit.MILLISECONDS)
            }
        }
        return controller.state.value
    }

    private fun CastState.toJson(): String {
        val pos = position
        val pageIndex = when (pos) {
            is CastPosition.Page -> pos.index
            is CastPosition.Transition -> if (pos.forward) pages.lastIndex else 0
        }.coerceAtLeast(0)
        val offset = (pos as? CastPosition.Page)?.offset ?: 0f
        val transition = (pos as? CastPosition.Transition)?.let {
            JSONObject()
                .put("title", it.title)
                .put("subtitle", it.subtitle ?: JSONObject.NULL)
        }
        val pagesJson = JSONArray()
        pages.forEach { page ->
            pagesJson.put(
                JSONObject()
                    .put("i", page.index)
                    .put("w", page.width)
                    .put("h", page.height),
            )
        }
        return JSONObject()
            .put("v", version)
            .put("active", active)
            .put("title", mangaTitle)
            .put("chapter", chapterName)
            .put("chapterId", chapterId)
            .put("mode", layoutMode.key)
            .put("rtl", rtl)
            .put("pages", pagesJson)
            .put("page", pageIndex)
            .put("offset", offset.toDouble())
            .put("transition", transition ?: JSONObject.NULL)
            .put("orientation", orientation.degrees)
            .put("scale", scaleMode.key)
            .put("zoom", zoomPercent)
            .put("stripWidth", stripWidthPercent)
            .put("background", background.cssColor)
            .put("pan", JSONObject().put("x", panX.toDouble()).put("y", panY.toDouble()))
            .toString()
    }

    // endregion

    // region Client tracking

    private fun touchClient(remoteIp: String) {
        val now = SystemClock.elapsedRealtime()
        val count = synchronized(clients) {
            clients[remoteIp] = now
            pruneClientsLocked(now)
        }
        reportClientCount(count)
    }

    private fun refreshClients() {
        val count = synchronized(clients) { pruneClientsLocked(SystemClock.elapsedRealtime()) }
        reportClientCount(count)
    }

    private fun pruneClientsLocked(now: Long): Int {
        clients.values.removeAll { lastSeen -> now - lastSeen > CLIENT_TIMEOUT_MS }
        return clients.size
    }

    private fun reportClientCount(count: Int) {
        if (!running) return
        synchronized(clients) {
            if (count == reportedClientCount) return
            reportedClientCount = count
        }
        controller.onWebClientsChanged(count)
    }

    // endregion

    companion object {
        private const val RECEIVER_ASSET = "cast/receiver.html"
        private const val CRLF = "\r\n"
        private const val JSON_CONTENT_TYPE = "application/json; charset=utf-8"

        private const val MAX_PORT = 65535
        private const val PORT_ATTEMPTS = 20
        private const val ACCEPT_BACKLOG = 50
        private const val MAX_CONNECTIONS = 8
        private const val SOCKET_TIMEOUT_MS = 30_000
        private const val INPUT_BUFFER_SIZE = 8 * 1024
        private const val OUTPUT_BUFFER_SIZE = 16 * 1024
        private const val MAX_LINE_LENGTH = 8 * 1024
        private const val MAX_HEADERS_LENGTH = 64 * 1024
        private const val MAX_EMPTY_LINES = 4

        private const val LONG_POLL_TIMEOUT_MS = 25_000L
        private const val IMAGE_TIMEOUT_MS = 70_000L
        private const val CLIENT_TIMEOUT_MS = 45_000L
        private const val CLIENT_REFRESH_MS = 15_000L

        private const val STATUS_OK = 200
        private const val STATUS_BAD_REQUEST = 400
        private const val STATUS_NOT_FOUND = 404
        private const val STATUS_METHOD_NOT_ALLOWED = 405
        private const val STATUS_INTERNAL_ERROR = 500
        private const val STATUS_UNAVAILABLE = 503
    }
}
