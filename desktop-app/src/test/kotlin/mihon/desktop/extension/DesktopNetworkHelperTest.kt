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
}
