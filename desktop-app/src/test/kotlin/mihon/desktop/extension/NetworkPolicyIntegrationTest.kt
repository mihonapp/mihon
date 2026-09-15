package mihon.desktop.extension

import com.sun.net.httpserver.HttpServer
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.extension.ipc.BrokerHttpRequest
import mihon.extension.ipc.NetworkFailureKind
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.InetSocketAddress
import java.nio.file.Path

class NetworkPolicyIntegrationTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `persisted proxy and user agent changes apply to the next request`(): Unit = runBlocking {
        val first = proxy("first")
        val second = proxy("second")
        try {
            val store = DesktopNetworkSettingsStore(DesktopPreferenceStore(directory.resolve("prefs.properties")))
            store.save(
                DesktopNetworkPolicy(DesktopProxyMode.HTTP, "127.0.0.1", first.address.port, userAgent = "Session/1"),
            )
            DesktopNetworkHelper(policyProvider = store::load).use { helper ->
                helper.registerExtensionDomains("test", listOf("source.invalid"))
                val request = BrokerHttpRequest("GET", "http://source.invalid/manga", extensionId = "test")
                helper.executeBrokeredRequest(request).body shouldBe "first:Session/1"
                store.save(store.load().copy(proxyPort = second.address.port, userAgent = "Session/2"))
                helper.executeBrokeredRequest(request).body shouldBe "second:Session/2"
            }
        } finally {
            first.stop(0)
            second.stop(0)
        }
    }

    @Test
    fun `verification and rate limit responses preserve status and retry headers`(): Unit = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/challenge") { exchange ->
            val bytes = "<html><script src='/cdn-cgi/challenge-platform/cf-chl-script'></script></html>".toByteArray()
            exchange.sendResponseHeaders(403, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.createContext("/limited") { exchange ->
            exchange.responseHeaders.add("Retry-After", "0")
            exchange.sendResponseHeaders(429, -1)
            exchange.close()
        }
        server.createContext("/blocked") { exchange ->
            val bytes = (
                "<title>Attention Required! | Cloudflare</title>" +
                    "<h1>Sorry, you have been blocked</h1>"
                ).toByteArray()
            exchange.responseHeaders.add("Server", "cloudflare")
            exchange.sendResponseHeaders(403, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.createContext("/header-challenge") { exchange ->
            exchange.responseHeaders.add("cf-mitigated", "challenge")
            exchange.sendResponseHeaders(403, -1)
            exchange.close()
        }
        server.start()
        try {
            DesktopNetworkHelper().use { helper ->
                helper.registerExtensionDomains("test", listOf("127.0.0.1"))
                val origin = "http://127.0.0.1:${server.address.port}"
                helper.executeBrokeredRequest(BrokerHttpRequest("GET", "$origin/blocked", extensionId = "test"))
                    .failureKind?.name shouldBe "SITE_BLOCKED"
                helper.executeBrokeredRequest(
                    BrokerHttpRequest("GET", "$origin/header-challenge", extensionId = "test"),
                )
                    .failureKind shouldBe NetworkFailureKind.WEB_VERIFICATION
                helper.executeBrokeredRequest(BrokerHttpRequest("GET", "$origin/challenge", extensionId = "test"))
                    .failureKind shouldBe NetworkFailureKind.WEB_VERIFICATION
                val response = helper.executeBrokeredRequest(
                    BrokerHttpRequest("GET", "$origin/limited", extensionId = "test"),
                )
                response.statusCode shouldBe 429
                response.failureKind shouldBe NetworkFailureKind.RATE_LIMITED
                response.headers.entries.first { it.key.equals("Retry-After", true) }.value shouldBe "0"
            }
        } finally {
            server.stop(0)
        }
    }

    private fun proxy(name: String): HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { exchange ->
            val bytes = "$name:${exchange.requestHeaders.getFirst("User-Agent")}".toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        start()
    }
}
