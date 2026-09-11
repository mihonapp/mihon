package mihon.desktop.extension

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import mihon.desktop.extension.builtin.BundledMangaDexSource
import mihon.extension.source.model.SChapter
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Test

class BundledMangaDexPageTest {
    private fun source(chapterJson: String) = BundledMangaDexSource(
        OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(
                    """{"baseUrl":"https://images.mangadex.network","chapter":$chapterJson}""".toResponseBody(),
                ).build()
        }.build(),
    )

    @Test
    fun `image requests preserve source user agent and referer`(): Unit = runBlocking {
        val source = source("""{"hash":"hash","data":["1.jpg"]}""")
        val page = source.getPageList(SChapter(url = "/chapter/id", name = "Chapter")).single()
        page.headers["User-Agent"] shouldBe source.headers["User-Agent"]
        page.headers["Referer"] shouldBe "https://mangadex.org/"
    }

    @Test
    fun `data saver fallback uses matching CDN directory`(): Unit = runBlocking {
        val page = source("""{"hash":"hash","data":[],"dataSaver":["small.jpg"]}""")
            .getPageList(SChapter(url = "/chapter/id", name = "Chapter")).single()
        page.imageUrl shouldBe "https://images.mangadex.network/data-saver/hash/small.jpg"
    }
}
