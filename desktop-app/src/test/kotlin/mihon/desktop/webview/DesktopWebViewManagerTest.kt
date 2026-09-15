package mihon.desktop.webview

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import mihon.desktop.extension.DesktopCookieStore
import mihon.desktop.extension.DesktopNetworkHelper
import mihon.extension.ipc.BrokerHttpRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path

class DesktopWebViewManagerTest {
    @TempDir lateinit var directory: Path

    @Test fun `ordinary HTTP does not launch helper and missing runtime fails explicitly`() = runBlocking {
        val cookies = DesktopCookieStore(directory.resolve("cookies"))
        val manager =
            DesktopWebViewManager(
                directory.resolve("absent"),
                directory,
                directory.resolve("cache"),
                DesktopNetworkHelper(cookieStore = cookies),
                cookies,
            )
        try {
            assertEquals(0, manager.activeSessionCount)
            val failure = runCatching { manager.open(1, "source", "https://example.org") }.exceptionOrNull()
            assertTrue(failure is IllegalStateException)
            assertEquals(0, manager.activeSessionCount)
        } finally {
            manager.close()
        }
    }

    @Test fun `real browser shares parent network headers cookie and closes process`() = runBlocking {
        val runtime = System.getenv("MIHON_WEBVIEW_TEST_RUNTIME")?.let(Path::of)
        assumeTrue(runtime != null)
        val distribution = Path.of(System.getenv("MIHON_WEBVIEW_TEST_DISTRIBUTION"))
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var sawHeader = false
        server.createContext("/") { exchange ->
            sawHeader = sawHeader || exchange.requestHeaders.getFirst("X-Test") == "shared"
            val content = if (exchange.requestURI.path == "/cookie") {
                exchange.requestHeaders.getFirst("Cookie").orEmpty()
            } else {
                "<html><title>Browser Test</title><script>window.answer=42;document.cookie='js_cookie=returned;path=/';</script></html>"
            }.toByteArray()
            exchange.responseHeaders.set("Content-Type", "text/html")
            exchange.sendResponseHeaders(200, content.size.toLong())
            exchange.responseBody.use { it.write(content) }
        }
        server.start()
        val url = "http://127.0.0.1:${server.address.port}/"
        val cookies = DesktopCookieStore(directory.resolve("cookies"))
        val network = DesktopNetworkHelper(cookieStore = cookies)
        network.registerExtensionDomains("test", listOf("127.0.0.1"))
        val manager = DesktopWebViewManager(runtime!!, distribution, directory.resolve("cache"), network, cookies)
        try {
            val session = manager.open(7, "test", url, mapOf("X-Test" to "shared"))
            session.awaitLoaded()
            assertEquals("42", session.evaluate("window.answer"))
            assertEquals("\"${network.userAgentFor(url)}\"", session.evaluate("navigator.userAgent"))
            assertTrue(sawHeader)
            session.exportCookies()
            val response = network.executeBrokeredRequest(
                BrokerHttpRequest("GET", url + "cookie", extensionId = "test", sourceId = 7),
            )
            assertTrue(response.body.orEmpty().contains("js_cookie=returned"))
            val pid = session.processId!!
            session.close()
            assertFalse(ProcessHandle.of(pid).map { it.isAlive }.orElse(false))
            assertEquals(0, manager.activeSessionCount)
            val retry = manager.open(7, "test", url)
            retry.awaitLoaded()
            val retryPid = retry.processId!!
            val failure = runCatching { retry.evaluate("new Promise(()=>{})", 100) }.exceptionOrNull()
            assertTrue(failure is kotlinx.coroutines.TimeoutCancellationException)
            assertFalse(ProcessHandle.of(retryPid).map { it.isAlive }.orElse(false))
            assertEquals(0, manager.activeSessionCount)
            val crashed = manager.open(7, "test", url)
            crashed.awaitLoaded()
            val crashedHandle = ProcessHandle.of(crashed.processId!!).orElseThrow()
            val descendants = crashedHandle.descendants().toList()
            crashedHandle.destroyForcibly()
            kotlinx.coroutines.withTimeout(10_000) {
                while (manager.activeSessionCount != 0 || descendants.any { it.isAlive }) kotlinx.coroutines.delay(20)
            }
            assertTrue(descendants.none { it.isAlive })
            // Exercise the Android-facing API used by translated extensions, against real JCEF.
            mihon.extension.host.WebViewBridge.install(manager::handleExtensionRequest)
            val identity = mihon.extension.host.ExtensionExecutionContext.Identity("test", 7, android.app.Application())
            val androidLoaded = java.util.concurrent.CompletableFuture<Unit>()
            lateinit var androidView: android.webkit.WebView
            mihon.extension.host.ExtensionExecutionContext.duringConstruction(identity) {
                androidView = android.webkit.WebView(identity.application)
                androidView.webViewClient = object : android.webkit.WebViewClient() {
                    override fun onPageFinished(
                        view: android.webkit.WebView?,
                        url: String?,
                    ) {
                        androidLoaded.complete(Unit)
                    }
                }
                androidView.loadUrl(url)
            }
            androidLoaded.get(20, java.util.concurrent.TimeUnit.SECONDS)
            val androidValue = java.util.concurrent.CompletableFuture<String>()
            mihon.extension.host.ExtensionExecutionContext.duringConstruction(identity) {
                android.webkit.CookieManager.getInstance().setCookie(url, "from_android=works; Path=/")
                androidView.evaluateJavascript("document.cookie") { androidValue.complete(it) }
            }
            assertTrue(androidValue.get(10, java.util.concurrent.TimeUnit.SECONDS).contains("from_android=works"))
            mihon.extension.host.ExtensionExecutionContext.duringConstruction(identity) { androidView.destroy() }
            assertEquals(0, manager.activeSessionCount)
        } finally {
            manager.close()
            server.stop(0)
        }
    }
}
