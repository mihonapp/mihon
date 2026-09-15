package mihon.desktop.extension

import com.sun.net.httpserver.HttpServer
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import mihon.extension.ipc.BrokerHttpRequest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.InetSocketAddress
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ExtensionNetworkSessionContractTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `extension cannot borrow another extensions declared network domains`(): Unit = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            exchange.sendResponseHeaders(200, 2)
            exchange.responseBody.use { it.write("ok".toByteArray()) }
        }
        server.start()
        try {
            val network = DesktopNetworkHelper()
            network.registerExtensionDomains("source-a", listOf("example.invalid"))
            network.registerExtensionDomains("source-b", listOf("127.0.0.1"))
            network.executeBrokeredRequest(request("source-a", server, "/")).statusCode shouldBe 403
            network.executeBrokeredRequest(request("source-b", server, "/")).statusCode shouldBe 200
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `response cookies persist by extension and obey request path`(@TempDir directory: Path): Unit = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            if (exchange.requestURI.path == "/login") {
                exchange.responseHeaders.add("Set-Cookie", "session=secret; Path=/private; Max-Age=3600")
            }
            val value = exchange.requestHeaders.getFirst("Cookie") ?: "none"
            val bytes = value.toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val file = directory.resolve("cookies.json")
            fun network() = DesktopNetworkHelper(cookieStore = DesktopCookieStore(file)).also {
                it.registerExtensionDomains("source-a", listOf("127.0.0.1"))
                it.registerExtensionDomains("source-b", listOf("127.0.0.1"))
            }
            network().executeBrokeredRequest(request("source-a", server, "/login"))
            val restored = network()
            restored.executeBrokeredRequest(request("source-a", server, "/private/page")).body shouldBe "session=secret"
            restored.executeBrokeredRequest(request("source-a", server, "/public")).body shouldBe "none"
            restored.executeBrokeredRequest(request("source-b", server, "/private/page")).body shouldBe "none"
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `cancellation interrupts a waiting network response`(): Unit = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        server.createContext("/") { exchange ->
            started.countDown()
            release.await(10, TimeUnit.SECONDS)
            runCatching {
                exchange.sendResponseHeaders(200, 2)
                exchange.responseBody.use { it.write("ok".toByteArray()) }
            }
            exchange.close()
        }
        server.start()
        try {
            val network = DesktopNetworkHelper().also {
                it.registerExtensionDomains("source-a", listOf("127.0.0.1"))
            }
            val loading = async { network.executeBrokeredRequest(request("source-a", server, "/slow")) }
            withContext(Dispatchers.IO) { started.await(3, TimeUnit.SECONDS) } shouldBe true
            withTimeout(1000) { loading.cancelAndJoin() }
        } finally {
            release.countDown()
            server.stop(0)
        }
    }

    private fun request(
        extensionId: String,
        server: HttpServer,
        path: String,
    ): BrokerHttpRequest = json.decodeFromString(
        """{"method":"GET","url":"http://127.0.0.1:${server.address.port}$path","extensionId":"$extensionId"}""",
    )
}
