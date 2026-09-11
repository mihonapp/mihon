package eu.kanade.tachiyomi.network

import io.kotest.matchers.shouldBe
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class SourceSessionTest {
    @Test
    fun `cookies obey domain path expiry and secure rules`() {
        val jar = NetworkHelper.createDefaultClient().cookieJar
        val origin = "https://example.org/login".toHttpUrl()
        jar.saveFromResponse(
            origin,
            listOf(
                Cookie.parse(origin, "session=ok; Domain=example.org; Path=/reader; Secure")!!,
                Cookie.parse(origin, "expired=no; Max-Age=0")!!,
            ),
        )
        jar.loadForRequest("https://cdn.example.org/reader/page".toHttpUrl()).map { it.name } shouldBe listOf("session")
        jar.loadForRequest("https://example.org/other".toHttpUrl()) shouldBe emptyList()
        jar.loadForRequest("http://example.org/reader".toHttpUrl()) shouldBe emptyList()
        jar.loadForRequest("https://badexample.org/reader".toHttpUrl()) shouldBe emptyList()
    }

    @Test
    fun `saved desktop session is read live without leaking to other domains`(@TempDir temp: Path) {
        val file = temp.resolve("cookies.json").toFile()
        file.writeText("""[{"domain":"example.org","cookies":{"session":"first"},"customUserAgent":"Browser UA"}]""")
        val interceptor = NetworkHelper.createDefaultClient(file).networkInterceptors
            .filterIsInstance<DesktopSessionInterceptor>().single()
        fun request(
            host: String,
        ) = interceptor.applyTo(Request.Builder().url("https://$host/reader").build())
        request("cdn.example.org").header("Cookie") shouldBe "session=first"
        request("cdn.example.org").header("User-Agent") shouldBe "Browser UA"
        request("badexample.org").header("Cookie") shouldBe null
        interceptor.applyTo(Request.Builder().url("http://example.org/reader").build()).header("Cookie") shouldBe null
        val explicit = Request.Builder().url(
            "https://example.org/",
        ).header("Cookie", "other=ok; session=explicit").build()
        interceptor.applyTo(explicit).header("Cookie") shouldBe "session=explicit; other=ok"
        file.writeText("[]")
        request("cdn.example.org").header("Cookie") shouldBe null
    }
}
