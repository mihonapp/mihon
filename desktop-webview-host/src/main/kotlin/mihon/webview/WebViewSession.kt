package mihon.webview

import com.jetbrains.cef.JCefAppConfig
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.cef.CefApp
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.browser.CefMessageRouter
import org.cef.callback.CefCallback
import org.cef.callback.CefQueryCallback
import org.cef.handler.CefLifeSpanHandlerAdapter
import org.cef.handler.CefLoadHandler
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.handler.CefMessageRouterHandlerAdapter
import org.cef.handler.CefRequestHandler
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.handler.CefResourceHandler
import org.cef.handler.CefResourceHandlerAdapter
import org.cef.handler.CefResourceRequestHandler
import org.cef.handler.CefResourceRequestHandlerAdapter
import org.cef.misc.BoolRef
import org.cef.misc.IntRef
import org.cef.misc.StringRef
import org.cef.network.CefCookie
import org.cef.network.CefCookieManager
import org.cef.network.CefPostDataElement
import org.cef.network.CefRequest
import org.cef.network.CefResponse
import java.awt.BorderLayout
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.Base64
import java.util.Date
import java.util.Vector
import java.util.concurrent.CompletableFuture
import javax.swing.JFrame
import javax.swing.SwingUtilities

internal class WebViewSession(
    private val config: JsonObject,
    private val identity: SessionIdentity,
    private val events: Events,
) : AutoCloseable {
    private val broker = URI(config.string("broker"))
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
    private val app: CefApp
    private val client: org.cef.CefClient
    private val browser: CefBrowser
    private val closed = java.util.concurrent.atomic.AtomicBoolean(false)
    private val window = JFrame("MihonW - Web verification")
    init {
        require(broker.host == "127.0.0.1" && broker.scheme == "http")
        val cef = JCefAppConfig.getInstance()
        cef.cefSettings.cache_path = config.string("cache")
        (config["headers"] as? JsonObject)?.entries?.firstOrNull {
            it.key.equals("User-Agent", true)
        }?.value?.jsonPrimitive?.content?.let {
            cef.cefSettings.user_agent =
                it
        }
        cef.cefSettings.log_file = Path.of(config.string("cache"), "cef.log").toString()
        cef.appArgsAsList.addAll(
            listOf(
                "--disable-background-networking",
                "--disable-quic",
                "--disable-component-update",
                "--disable-sync",
                "--proxy-server=http://127.0.0.1:9",
                "--proxy-bypass-list=<-loopback>",
            ),
        )
        check(CefApp.startup(cef.appArgs)) { "JCEF startup failed" }
        app = CefApp.getInstance(cef.appArgs, cef.cefSettings)
        client = app.createClient()
        val router = CefMessageRouter.create()
        router.addHandler(
            object : CefMessageRouterHandlerAdapter() {
                override fun onQuery(
                    browser: CefBrowser?,
                    frame: CefFrame?,
                    queryId: Long,
                    request: String?,
                    persistent: Boolean,
                    callback: CefQueryCallback?,
                ): Boolean {
                    val value =
                        runCatching { wireJson.parseToJsonElement(request.orEmpty()).jsonObject }.getOrNull()
                            ?: return false
                    events.send("result", value.string("id"), value.string("value"))
                    callback?.success("")
                    return true
                }
            },
            true,
        )
        client.addMessageRouter(router)
        client.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(browser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                if (frame?.isMain == true) events.send("loaded", value = browser?.url.orEmpty())
            }
            override fun onLoadError(
                browser: CefBrowser?,
                frame: CefFrame?,
                errorCode: CefLoadHandler.ErrorCode?,
                errorText: String?,
                failedUrl: String?,
            ) {
                if (frame?.isMain == true) events.send("error", value = "$errorCode: $errorText")
            }
        })
        client.addRequestHandler(object : CefRequestHandlerAdapter() {
            override fun onBeforeBrowse(
                browser: CefBrowser?,
                frame: CefFrame?,
                request: CefRequest?,
                userGesture: Boolean,
                isRedirect: Boolean,
            ): Boolean =
                request?.url?.let { !allowedScheme(it) } ?: true
            override fun getResourceRequestHandler(
                browser: CefBrowser?,
                frame: CefFrame?,
                request: CefRequest?,
                isNavigation: Boolean,
                isDownload: Boolean,
                requestInitiator: String?,
                disableDefaultHandling: BoolRef?,
            ): CefResourceRequestHandler {
                val network = request?.url?.startsWith("http") == true
                if (network) disableDefaultHandling?.set(true)
                return object : CefResourceRequestHandlerAdapter() {
                    override fun getResourceHandler(
                        browser: CefBrowser?,
                        frame: CefFrame?,
                        request: CefRequest?,
                    ): CefResourceHandler? = if (network) BrokerResource() else null
                }
            }
            override fun onRenderProcessTerminated(
                browser: CefBrowser?,
                status: CefRequestHandler.TerminationStatus?,
                errorCode: Int,
                errorString: String?,
            ) {
                events.send("error", value = "Renderer terminated: $status ($errorCode)")
            }
        })
        client.addLifeSpanHandler(object : CefLifeSpanHandlerAdapter() {
            override fun onAfterCreated(browser: CefBrowser) {
                browser.loadURL(config.string("url"))
            }
            override fun onBeforePopup(
                browser: CefBrowser?,
                frame: CefFrame?,
                targetUrl: String?,
                targetFrameName: String?,
            ): Boolean = true
        })
        browser = client.createBrowser("about:blank", false, false)
        SwingUtilities.invokeAndWait {
            window.defaultCloseOperation = JFrame.DO_NOTHING_ON_CLOSE
            window.addWindowListener(object : WindowAdapter() {
                override fun windowClosing(event: WindowEvent?) {
                    exportCookies("__closing__")
                }
            })
            window.contentPane.add(browser.uiComponent, BorderLayout.CENTER)
            window.setSize(1000, 760)
            window.isVisible = true
        }
        val cookies = config["cookies"] as? JsonArray ?: JsonArray(emptyList())
        cookies.forEach { item ->
            val cookie = item.jsonObject
            CefCookieManager.getGlobalManager().setCookie(
                config.string("url"),
                CefCookie(
                    cookie.string("name"), cookie.string("value"), cookie.string("domain"), cookie.string("path"),
                    cookie.string(
                        "secure",
                    ) == "true",
                    cookie.string("httpOnly") == "true", Date(), Date(), false, Date(),
                ),
            )
        }
        events.send("ready", value = System.getProperty("java.version"))
    }
    fun command(message: JsonObject) {
        require(identity.accepts(message)) { "Session identity mismatch" }
        when (message.string("type")) {
            "navigate" -> {
                val url = message.string("url")
                require(allowedScheme(url))
                browser.loadURL(url)
            }
            "evaluate" -> {
                val id = JsonPrimitive(message.string("id")).toString()
                val script = JsonPrimitive(message.string("script")).toString()
                val queryScript = "Promise.resolve().then(()=>eval($script))" +
                    ".then(v=>window.cefQuery({request:JSON.stringify({id:$id,value:JSON.stringify(v)})}))" +
                    ".catch(e=>window.cefQuery({request:JSON.stringify({id:$id,value:String(e)})}));"
                browser.executeJavaScript(queryScript, browser.url, 0)
            }
            "source" -> browser.getSource { source -> events.send("result", message.string("id"), source) }
            "cookies" -> exportCookies(message.string("id"))
            "setCookie" -> {
                val cookie = message["cookie"]!!.jsonObject
                val expires = cookie.string("expiresAt").toLongOrNull() ?: 0
                val manager = CefCookieManager.getGlobalManager()
                manager.setCookie(
                    message.string("url"),
                    CefCookie(
                        cookie.string("name"), cookie.string("value"), cookie.string("domain"), cookie.string("path"),
                        cookie.string(
                            "secure",
                        ) == "true",
                        cookie.string("httpOnly") == "true", Date(), Date(), expires > 0, Date(expires),
                    ),
                )
                manager.flushStore { events.send("result", message.string("id"), "true") }
            }
            "close" -> close()
        }
    }
    private fun exportCookies(id: String) {
        val manager = CefCookieManager.getGlobalManager()
        manager.visitAllCookies { cookie, count, total, _ ->
            events.send(
                "cookie",
                id,
                buildJsonObject {
                    put("name", cookie.name)
                    put("value", cookie.value)
                    put("domain", cookie.domain)
                    put("path", cookie.path)
                    put("secure", cookie.secure)
                    put("httpOnly", cookie.httponly)
                    put("expiresAt", if (cookie.hasExpires) cookie.expires.time else 0)
                }.toString(),
            )

            true
        }
        manager.flushStore {
            events.send("cookiesDone", id)
            if (id == "__closing__") {
                events.send("closed")
                SwingUtilities.invokeLater { close() }
            }
        }
    }
    private fun allowedScheme(url: String) =
        url == "chrome://credits/" || URI(url).scheme in setOf("http", "https", "about", "data", "blob")
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        browser.stopLoad()
        browser.close(true)
        client.dispose()
        app.dispose()
        SwingUtilities.invokeLater { window.dispose() }
    }
    private inner class BrokerResource : CefResourceHandlerAdapter() {
        private var bytes = ByteArray(0)
        private var offset = 0
        private var requestedUrl = ""
        private var result = buildJsonObject { put("statusCode", 502) }
        private var requestFuture: CompletableFuture<*>? = null
        override fun processRequest(request: CefRequest, callback: CefCallback): Boolean {
            requestedUrl = request.url
            val headers = mutableMapOf<String, String>()
            request.getHeaderMap(headers)
            if (URI(request.url).authority == URI(config.string("url")).authority) {
                (config["headers"] as? JsonObject)?.forEach { (key, value) ->
                    headers.putIfAbsent(key, value.jsonPrimitive.content)
                }
            }
            val elements = Vector<CefPostDataElement>()
            request.postData?.getElements(elements)
            val body = elements.flatMap { element ->
                require(element.type != CefPostDataElement.Type.PDE_TYPE_FILE) {
                    "Local file uploads are not supported"
                }
                val data = ByteArray(element.bytesCount)
                element.getBytes(data.size, data)
                data.asIterable()
            }.toByteArray()
            val payload = buildJsonObject {
                put("sessionId", identity.sessionId)
                put("sourceId", identity.sourceId)
                put("token", identity.token)
                put("method", request.method)
                put("url", request.url)
                put("headers", buildJsonObject { headers.forEach { (key, value) -> put(key, value) } })
                put("bodyBase64", Base64.getEncoder().encodeToString(body))
            }
            val call = HttpRequest.newBuilder(
                broker,
            ).timeout(Duration.ofSeconds(45)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString())).build()
            requestFuture =
                http.sendAsync(call, HttpResponse.BodyHandlers.ofString()).whenComplete { response, failure ->
                    if (failure == null) {
                        runCatching {
                            result = wireJson.parseToJsonElement(response.body()).jsonObject
                            bytes = Base64.getDecoder().decode(result.string("bodyBase64"))
                        }
                    }
                    callback.Continue()
                }
            return true
        }
        override fun getResponseHeaders(response: CefResponse, responseLength: IntRef, redirectUrl: StringRef) {
            val finalUrl = result.string("finalUrl")
            if (finalUrl.isNotBlank() && finalUrl != requestedUrl) {
                redirectUrl.set(finalUrl)
                response.status = 302
                responseLength.set(0)
                bytes = ByteArray(0)
                return
            }
            response.status = result.string("statusCode").toIntOrNull() ?: 502
            val headers = result["headers"] as? JsonObject ?: buildJsonObject { }
            response.setHeaderMap(
                headers.mapValues { it.value.jsonPrimitive.content }.filterKeys {
                    !it.equals("Content-Encoding", true) &&
                        !it.equals("Transfer-Encoding", true)
                },
            )
            response.mimeType =
                headers.entries.firstOrNull {
                    it.key.equals("Content-Type", true)
                }?.value?.jsonPrimitive?.content?.substringBefore(';')
                    ?: "text/html"
            responseLength.set(bytes.size)
        }
        override fun readResponse(
            dataOut: ByteArray,
            bytesToRead: Int,
            bytesRead: IntRef,
            callback: CefCallback,
        ): Boolean {
            val count = minOf(bytesToRead, bytes.size - offset)
            if (count <= 0) {
                bytesRead.set(0)
                return false
            }
            bytes.copyInto(dataOut, 0, offset, offset + count)
            offset += count
            bytesRead.set(count)
            return true
        }
        override fun cancel() {
            requestFuture?.cancel(true)
        }
    }
}
