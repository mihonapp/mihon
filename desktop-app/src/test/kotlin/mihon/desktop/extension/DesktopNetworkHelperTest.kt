package mihon.desktop.extension

import com.sun.net.httpserver.HttpServer
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import mihon.extension.ipc.BrokerHttpRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress

class DesktopNetworkHelperTest {

    private lateinit var server: HttpServer
    private lateinit var helper: DesktopNetworkHelper
    private var serverPort: Int = 0

    @BeforeEach
    fun setUp() {
        server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/test") { exchange ->
            val response = "Hello from mock server".toByteArray()
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.start()
        serverPort = server.address.port
        helper = DesktopNetworkHelper()
    }

    @AfterEach
    fun tearDown() {
        server.stop(0)
    }

    @Test
    fun `rejects request when domain is not in whitelist`() {
        runBlocking {
            val url = "http://127.0.0.1:" + serverPort + "/test"
            val req = BrokerHttpRequest(
                method = "GET",
                url = url,
            )

            val res = helper.executeBrokeredRequest(req)
            res.statusCode shouldBe 403
            res.error?.contains("Access denied") shouldBe true
        }
    }

    @Test
    fun `allows request when exact domain or wildcard is registered`() {
        runBlocking {
            helper.registerExtensionDomains("test.ext", listOf("127.0.0.1"))

            val url = "http://127.0.0.1:" + serverPort + "/test"
            val req = BrokerHttpRequest(
                method = "GET",
                url = url,
            )

            val res = helper.executeBrokeredRequest(req)
            res.statusCode shouldBe 200
            res.body shouldBe "Hello from mock server"

            // Unregister and ensure blocked
            helper.unregisterExtensionDomains("test.ext")
            val blockedRes = helper.executeBrokeredRequest(req)
            blockedRes.statusCode shouldBe 403
        }
    }

    @Test
    fun `wildcard domain matches subdomains`() {
        helper.registerExtensionDomains("test.wildcard", listOf("*.example.com"))
        helper.isDomainAllowed("example.com") shouldBe true
        helper.isDomainAllowed("api.example.com") shouldBe true
        helper.isDomainAllowed("v2.api.example.com") shouldBe true
        helper.isDomainAllowed("evil-example.com") shouldBe false
        helper.isDomainAllowed("notexample.com") shouldBe false
    }

    @Test
    fun `injects cookies and custom user agent from cookie store`() {
        var receivedCookie: String? = null
        var receivedUa: String? = null

        server.createContext("/header-check") { exchange ->
            receivedCookie = exchange.requestHeaders.getFirst("Cookie")
            receivedUa = exchange.requestHeaders.getFirst("User-Agent")
            val resp = "OK".toByteArray()
            exchange.sendResponseHeaders(200, resp.size.toLong())
            exchange.responseBody.use { it.write(resp) }
        }

        val tempCookieFile = java.nio.file.Files.createTempFile("cookies-test", ".json")
        try {
            val cookieStore = DesktopCookieStore(tempCookieFile)
            cookieStore.setCookies(
                domain = "127.0.0.1",
                cookies = mapOf("cf_clearance" to "cf123", "token" to "tok456"),
                customUserAgent = "MihonBypassUA/2.0",
            )

            val helperWithCookies = DesktopNetworkHelper(cookieStore = cookieStore)
            helperWithCookies.registerExtensionDomains("local", listOf("127.0.0.1"))

            runBlocking {
                val res = helperWithCookies.executeBrokeredRequest(
                    BrokerHttpRequest(
                        method = "GET",
                        url = "http://127.0.0.1:$serverPort/header-check",
                    ),
                )
                res.statusCode shouldBe 200
                receivedCookie shouldBe "cf_clearance=cf123; token=tok456"
                receivedUa shouldBe "MihonBypassUA/2.0"
            }
        } finally {
            java.nio.file.Files.deleteIfExists(tempCookieFile)
        }
    }
}
