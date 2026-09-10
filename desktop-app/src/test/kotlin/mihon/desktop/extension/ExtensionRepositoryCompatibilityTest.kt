package mihon.desktop.extension

import com.sun.net.httpserver.HttpServer
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import mihon.desktop.preferences.DesktopPreferenceStore
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.InetSocketAddress
import java.nio.file.Path

class ExtensionRepositoryCompatibilityTest {

    @Test
    fun `modern repository JSON object uses the network store schema`(@TempDir tempDir: Path) {
        val body = """
            {
              "name":"JSON repository",
              "badgeLabel":"JSON",
              "signingKey":"AABBCC",
              "contact":{"website":"https://repo.example"},
              "extensionList":{"extensions":[{
                "name":"JSON Extension",
                "packageName":"ext.json",
                "resources":{"apkUrl":"apk/ext.json.apk","iconUrl":"icon/ext.json.png"},
                "extensionLib":"1.6",
                "versionCode":7,
                "versionName":"1.6.7",
                "contentWarning":"CONTENT_WARNING_SAFE",
                "sources":[{"id":7,"name":"JSON Source","language":"en"}]
              }]}
            }
        """.trimIndent().toByteArray()
        val server = repositoryServer(
            responses = mapOf(
                "/repo/index.pb" to HttpResponse(200, body),
            ),
        )

        try {
            val service = service(tempDir)
            val items = runBlocking {
                service.fetchRepository("http://127.0.0.1:${server.address.port}/repo")
            }

            items shouldHaveSize 1
            items.single().pkg shouldBe "ext.json"
            items.single().signingKey shouldBe "AABBCC"
            items.single().isNsfw shouldBe false
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `legacy JSON fallback accepts UTF-8 BOM and leading whitespace`(@TempDir tempDir: Path) {
        val json = """
            [
              {"pkg":"ext.bom","name":"BOM Repo","version":"1.0.0","code":1,"apk":"bom.apk"}
            ]
        """.trimIndent()
        val body = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            " \r\n\t$json".toByteArray()
        val server = repositoryServer(
            responses = mapOf(
                "/repo/index.pb" to HttpResponse(404),
                "/repo/index.min.json" to HttpResponse(200, body),
                "/repo/repo.json" to HttpResponse(404),
            ),
        )

        try {
            val service = service(tempDir)
            val items = runBlocking {
                service.fetchRepository("http://127.0.0.1:${server.address.port}/repo")
            }

            items shouldHaveSize 1
            items.single().pkg shouldBe "ext.bom"
        } finally {
            server.stop(0)
        }
    }

    @Test
    @OptIn(ExperimentalSerializationApi::class)
    fun `protobuf index resolves a relative extension list URL`(@TempDir tempDir: Path) {
        val list = DesktopNetworkExtensionStore.ExtensionList(
            extensions = listOf(
                DesktopNetworkExtensionStore.Extension(
                    name = "Split Index",
                    packageName = "ext.split",
                    versionName = "1.6.0",
                    versionCode = 16,
                    extensionLib = "1.6",
                    resources = DesktopNetworkExtensionStore.Resources(jarUrl = "jar/ext.split.jar"),
                ),
            ),
        )
        val store = DesktopNetworkExtensionStore(
            name = "Split repository",
            extensionListUrl = "lists/extensions.pb",
        )
        val server = repositoryServer(
            responses = mapOf(
                "/repo/index.pb" to HttpResponse(200, ProtoBuf.encodeToByteArray(store)),
                "/repo/lists/extensions.pb" to HttpResponse(200, ProtoBuf.encodeToByteArray(list)),
                "/repo/repo.json" to HttpResponse(404),
            ),
        )

        try {
            val service = service(tempDir)
            val items = runBlocking {
                service.fetchRepository("http://127.0.0.1:${server.address.port}/repo")
            }

            items shouldHaveSize 1
            items.single().downloadUrl shouldBe
                "http://127.0.0.1:${server.address.port}/repo/jar/ext.split.jar"
            items.single().libVersion shouldBe 1.6
        } finally {
            server.stop(0)
        }
    }

    private fun service(tempDir: Path): ExtensionStoreService =
        ExtensionStoreService(DesktopPreferenceStore(tempDir.resolve("prefs.properties")))

    private fun repositoryServer(responses: Map<String, HttpResponse>): HttpServer =
        HttpServer.create(InetSocketAddress(0), 0).apply {
            responses.forEach { (path, response) ->
                createContext(path) { exchange ->
                    exchange.sendResponseHeaders(response.status, response.body.size.toLong())
                    exchange.responseBody.use { output ->
                        if (response.body.isNotEmpty()) output.write(response.body)
                    }
                }
            }
            start()
        }

    private data class HttpResponse(
        val status: Int,
        val body: ByteArray = byteArrayOf(),
    )
}
