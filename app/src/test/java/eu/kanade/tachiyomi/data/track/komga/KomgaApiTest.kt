package eu.kanade.tachiyomi.data.track.komga

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton

private const val BOOK_URL = "https://example.com/api/v1/books/abc-123"

class KomgaApiTest {

    @BeforeEach
    fun setUp() {
        Injekt.addSingleton(
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            },
        )
    }

    private fun apiRespondingWith(body: String): KomgaApi {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()
        return KomgaApi(trackId = 6L, client = client)
    }

    @Test
    fun `pull distinguishes a genuinely absent read progress from page zero`() = runTest {
        val api = apiRespondingWith("""{"id":"abc-123"}""")

        val progress = api.getBookReadProgress(BOOK_URL)

        progress shouldBe KomgaBookProgress.Absent
    }

    @Test
    fun `pull parses a recorded first page, including it distinctly from absent`() = runTest {
        // Komga's page 1 is the first page; still distinct from no progress recorded.
        val api = apiRespondingWith(
            """{"id":"abc-123","readProgress":{"page":1,"completed":false}}""",
        )

        val progress = api.getBookReadProgress(BOOK_URL)

        // Converted to Mihon's 0-indexed convention.
        progress shouldBe KomgaBookProgress.Recorded(0)
    }

    @Test
    fun `pull parses a recorded page in progress, converting Komga's 1-indexed page to 0-indexed`() = runTest {
        val api = apiRespondingWith(
            """{"id":"abc-123","readProgress":{"page":42,"completed":false}}""",
        )

        val progress = api.getBookReadProgress(BOOK_URL)

        progress shouldBe KomgaBookProgress.Recorded(41)
    }

    @Test
    fun `push converts Mihon's 0-indexed page to Komga's 1-indexed page`() = runTest {
        var capturedUrl: String? = null
        var capturedMethod: String? = null
        var capturedBody: String? = null

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                capturedUrl = request.url.toString()
                capturedMethod = request.method
                val buffer = okio.Buffer()
                request.body?.writeTo(buffer)
                capturedBody = buffer.readUtf8()

                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(204)
                    .message("No Content")
                    .body("".toResponseBody(null))
                    .build()
            }
            .build()
        val api = KomgaApi(trackId = 6L, client = client)

        api.updateBookReadProgress(BOOK_URL, 17)

        capturedUrl shouldBe "$BOOK_URL/read-progress"
        capturedMethod shouldBe "PATCH"
        capturedBody shouldBe """{"page":18,"completed":false}"""
    }
}
