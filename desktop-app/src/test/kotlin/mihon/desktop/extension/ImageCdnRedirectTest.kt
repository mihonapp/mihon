package mihon.desktop.extension

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import mihon.extension.ipc.BrokerHttpRequest
import mihon.extension.ipc.NetworkFailureKind
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Test

class ImageCdnRedirectTest {
    private val owner = "eu.kanade.tachiyomi.extension.all.nhentaixxx"
    private val original = "https://i4.nhentai.xxx/016/fixture/1.jpg"
    private val migrated = "https://i4.nhentaimg.com/016/fixture/1.jpg"

    @Test
    fun `saved image follows the verified CDN migration without granting the new host`(): Unit = runBlocking {
        val requests = mutableListOf<Request>()
        network(requests, migrated).use { network ->
            network.registerRuntimePageUrl(original, extensionId = owner)
            val response = network.executeBrokeredRequest(
                BrokerHttpRequest(
                    "GET",
                    original,
                    extensionId = owner,
                    headers = mapOf("Authorization" to "Bearer source-only", "Cookie" to "source=secret"),
                ),
            )
            response.statusCode shouldBe 200
            response.finalUrl shouldBe migrated
            requests.map { it.url.toString() } shouldBe listOf(original, migrated)
            requests.last().header("Authorization") shouldBe null
            requests.last().header("Cookie") shouldBe null
            network.isDomainAllowed("i4.nhentaimg.com", owner) shouldBe false
            network.executeBrokeredRequest(BrokerHttpRequest("GET", migrated, extensionId = owner))
                .failureKind shouldBe NetworkFailureKind.DOMAIN_DENIED
            requests.size shouldBe 2
        }
    }

    @Test
    fun `migration cannot authorize other extensions or unrelated redirect destinations`(): Unit = runBlocking {
        val attempts = listOf(
            "another.extension" to migrated,
            owner to "https://unrelated.example/016/fixture/1.jpg",
            owner to "https://i4.nhentaimg.com.evil.example/016/fixture/1.jpg",
            owner to "http://i4.nhentaimg.com/016/fixture/1.jpg",
            owner to "https://i4.nhentaimg.com:8443/016/fixture/1.jpg",
            owner to "https://i5.nhentaimg.com/016/fixture/1.jpg",
            owner to "https://i4.nhentaimg.com/other-path",
            owner to "$migrated?unexpected=query",
            owner to "https://127.0.0.1/016/fixture/1.jpg",
        )
        attempts.forEach { (extension, target) ->
            val requests = mutableListOf<Request>()
            network(requests, target).use { network ->
                network.registerRuntimePageUrl(original, extensionId = extension)
                network.executeBrokeredRequest(BrokerHttpRequest("GET", original, extensionId = extension))
                    .failureKind shouldBe NetworkFailureKind.DOMAIN_DENIED
                requests.size shouldBe 1
            }
        }
    }

    @Test
    fun `migration still checks initial permission and subsequent redirects`(): Unit = runBlocking {
        val requests = mutableListOf<Request>()
        network(requests, migrated, "https://unrelated.example/secret").use { network ->
            network.executeBrokeredRequest(BrokerHttpRequest("GET", original, extensionId = owner))
                .failureKind shouldBe NetworkFailureKind.DOMAIN_DENIED
            requests.size shouldBe 0
            network.registerRuntimePageUrl(original, extensionId = owner)
            network.executeBrokeredRequest(BrokerHttpRequest("GET", original, extensionId = owner))
                .failureKind shouldBe NetworkFailureKind.DOMAIN_DENIED
            requests.map { it.url.toString() } shouldBe listOf(original, migrated)
        }
    }

    private fun network(
        requests: MutableList<Request>,
        target: String,
        subsequentRedirect: String? = null,
    ): DesktopNetworkHelper = DesktopNetworkHelper(
        client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            requests.add(request)
            val redirect = if (request.url.toString() == original) target else subsequentRedirect
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(if (redirect == null) 200 else 301).message("fixture")
                .apply { redirect?.let { header("Location", it) } }
                .body("image fixture".toResponseBody()).build()
        }.build(),
    )
}
