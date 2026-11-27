package eu.kanade.tachiyomi.data.sync.webdav

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
class WebDavClientTest {

    @Test
    fun `buildFileUrl handles trailing slashes and nested remote paths without double slashes`() {
        val urlWithSlash = WebDavClient.buildFileUrl(
            serverUrl = "https://dav.example.com/dav/",
            remotePath = "/mihon/sync/",
            fileName = "mihon_sync_v1.json.gz",
        )
        urlWithSlash.toString() shouldBe "https://dav.example.com/dav/mihon/sync/mihon_sync_v1.json.gz"

        val urlWithoutSlash = WebDavClient.buildFileUrl(
            serverUrl = "https://dav.example.com/dav",
            remotePath = "mihon/../sync",
            fileName = "mihon_sync_v1.json.gz",
        )
        urlWithoutSlash.toString() shouldBe "https://dav.example.com/dav/mihon/sync/mihon_sync_v1.json.gz"
    }

    @Test
    fun `buildDirectoryUrls returns each intermediate directory with trailing slash`() {
        val dirs = WebDavClient.buildDirectoryUrls(
            serverUrl = "https://dav.example.com/dav/",
            remotePath = "/mihon/backups/",
        )
        dirs.map { it.toString() } shouldBe listOf(
            "https://dav.example.com/dav/mihon/",
            "https://dav.example.com/dav/mihon/backups/",
        )
    }

    @Test
    fun `upload creates directories ignoring 405 and returns etag`() = runTest {
        val seenMethods = mutableListOf<Pair<String, String>>()
        val okHttp = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                seenMethods += req.method to req.url.encodedPath
                val code = when (req.method) {
                    "MKCOL" -> 405
                    "PUT" -> 201
                    else -> 200
                }
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("OK")
                    .header("ETag", "\"etag-1\"")
                    .body(ByteArray(0).toResponseBody(null))
                    .build()
            }
            .build()

        val client = WebDavClient(okHttp)
        val config = WebDavConfig("https://dav.example.com/dav/", "alice", "secret", "/mihon")

        val etag = client.upload(config, "mihon_sync_v1.json.gz", byteArrayOf(1, 2, 3))

        etag shouldBe "\"etag-1\""
        seenMethods shouldBe listOf(
            "MKCOL" to "/dav/mihon/",
            "PUT" to "/dav/mihon/mihon_sync_v1.json.gz",
        )
    }

    @Test
    fun `download returns null on 404 and throws WebDavException on 401`() = runTest {
        val okHttp404 = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(404)
                    .message("Not Found")
                    .body(ByteArray(0).toResponseBody(null))
                    .build()
            }
            .build()

        val config = WebDavConfig("https://dav.example.com/dav", "alice", "wrong", "/mihon")
        WebDavClient(okHttp404).download(config, "mihon_sync_v1.json.gz") shouldBe null

        val okHttp401 = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(401)
                    .message("Unauthorized")
                    .body(ByteArray(0).toResponseBody(null))
                    .build()
            }
            .build()

        val error = assertThrows<WebDavException> {
            WebDavClient(okHttp401).download(config, "mihon_sync_v1.json.gz")
        }
        error.statusCode shouldBe 401
    }
}
