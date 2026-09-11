package mihon.extension.compat

import eu.kanade.tachiyomi.source.online.HttpSource
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mihon.extension.host.BrokeredHttpClient
import mihon.extension.host.ExtensionHostEngine
import mihon.extension.ipc.IpcRequest
import mihon.extension.source.model.SChapter
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Test
import java.io.File
import eu.kanade.tachiyomi.source.model.Page as TPage
import eu.kanade.tachiyomi.source.model.SChapter as TChapter

class SourceImagePipelineTest {
    private class ImageSource : HttpSource() {
        override val id = 123L
        override val name = "Image contract fixture"
        override val lang = "en"
        override val supportsLatest = false
        override val baseUrl = "https://fixture.invalid"
        var resolutions = 0
        var imageRequests = 0
        override val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            request.method shouldBe "POST"
            request.header("X-Image-Token") shouldBe "source-token"
            request.url.encodedPath shouldBe "/actual-image"
            imageRequests++
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(
                    byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xe0.toByte())
                        .toResponseBody("image/jpeg".toMediaType()),
                ).build()
        }.build()
        override suspend fun getPageList(chapter: TChapter) = listOf(TPage(7, "$baseUrl/view/7"))
        override suspend fun getImageUrl(page: TPage): String {
            resolutions++
            return "$baseUrl/actual-image"
        }
        override fun imageRequest(page: TPage): Request = Request.Builder().url(page.imageUrl!!)
            .header("X-Image-Token", "source-token").post("image".toRequestBody()).build()
    }

    @Test
    fun `page discovery does not resolve every image eagerly`(): Unit = runBlocking {
        val delegate = ImageSource()
        val pages = TachiyomiCatalogueSourceAdapter(delegate).getPageList(SChapter(url = "/chapter", name = ""))
        delegate.resolutions shouldBe 0
        pages.single().index shouldBe 0
    }

    @Test
    fun `host downloads with source image request and source client`(): Unit = runBlocking {
        val delegate = ImageSource()
        val engine = ExtensionHostEngine(BrokeredHttpClient { null })
        engine.registerSource(TachiyomiCatalogueSourceAdapter(delegate))
        val result = engine.handleRequest(
            IpcRequest(
                1,
                "get_image",
                """
            {"sourceId":123,"page":{"index":0,"url":"https://fixture.invalid/view/7"}}
                """.trimIndent(),
            ),
        )
        result.success shouldBe true
        delegate.resolutions shouldBe 1
        delegate.imageRequests shouldBe 1
        val name = Json.parseToJsonElement(result.payloadJson).jsonObject.getValue("fileName").jsonPrimitive.content
        val file = File("page-images", name)
        try {
            file.readBytes().size shouldBe 4
        } finally {
            file.delete()
        }
    }
}
