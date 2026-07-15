package eu.kanade.tachiyomi.data.recommendation

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.test.runTest
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.seconds

class NhentaiRelatedProviderTest {

    @Test
    fun `supports only HttpSource on exact nhentai host with gallery path`() {
        val provider = DefaultNhentaiRelatedProvider()
        val client = responseClient(200, "{\"result\":[]}").first

        assertFalse(provider.supportsForTest(NonHttpSource, manga("/g/42/")))
        assertFalse(provider.supportsForTest(TestHttpSource("https://evilnhentai.net", client), manga("/g/42/")))
        assertFalse(provider.supportsForTest(TestHttpSource("https://nhentai.net.evil.test", client), manga("/g/42/")))
        assertFalse(provider.supportsForTest(TestHttpSource("https://nhentai.net", client), manga("/gallery/42/")))
        assertFalse(
            provider.supportsForTest(
                TestHttpSource("https://nhentai.net", client),
                manga("https://evil.test/g/42/"),
            ),
        )
        assertTrue(provider.supportsForTest(TestHttpSource("https://nhentai.net", client), manga("/g/42/")))
        assertTrue(
            provider.supportsForTest(
                TestHttpSource("https://api.nhentai.net", client),
                manga("https://www.nhentai.net/g/42/"),
            ),
        )
    }

    @Test
    fun `loads related galleries with source client headers and no detail calls`() = runTest {
        val body =
            """
            {
              "result": [
                {
                  "id": 42,
                  "media_id": "current",
                  "title": {"pretty": "Current"},
                  "images": {"thumbnail": {"t": "j"}},
                  "tags": []
                },
                {
                  "id": "100",
                  "media_id": 9876,
                  "title": {
                    "english": "English fallback",
                    "pretty": "Pretty title",
                    "japanese": "日本語"
                  },
                  "images": {
                    "cover": {"t": "j"},
                    "thumbnail": {"t": "p"}
                  },
                  "tags": [
                    {"type": "artist", "name": "Artist A"},
                    {"type": "author", "name": "Writer A"},
                    {"type": "group", "name": "Circle A"},
                    {"type": "parody", "name": "Original"},
                    {"type": "tag", "name": "Romance"},
                    {"type": "language", "name": "English"},
                    {"type": "TAG", "name": "romance"}
                  ]
                },
                {"id": 101, "media_id": "broken", "title": {}}
              ],
              "num_pages": 1,
              "new_server_field": true
            }
            """.trimIndent()
        val (client, calls, capturedRequest) = responseClient(
            code = 200,
            body = body,
            contentType = "application/json; charset=utf-8",
        )
        val source = TestHttpSource("https://www.nhentai.net", client)
        val provider = DefaultNhentaiRelatedProvider()

        val outcome = provider.loadForTest(source, manga("/g/42/?utm_source=test"))

        val success = assertInstanceOf(NhentaiRelatedOutcome.Success::class.java, outcome)
        assertEquals(1, success.manga.size)
        success.manga.single().let { related ->
            assertEquals("/g/100/", related.url)
            assertEquals("Pretty title", related.title)
            assertEquals("Artist A", related.artist)
            assertEquals("Writer A, Circle: Circle A", related.author)
            assertEquals("Original, Romance, English", related.genre)
            assertEquals("https://t.nhentai.net/galleries/9876/thumb.png", related.thumbnail_url)
            assertEquals(SManga.COMPLETED, related.status)
            assertFalse(related.initialized)
        }
        assertEquals(1, calls.get())
        assertEquals(0, source.detailCalls.get())
        assertEquals("GET", capturedRequest.get().method)
        assertEquals("https://www.nhentai.net/api/gallery/42/related", capturedRequest.get().url.toString())
        assertEquals("test-value", capturedRequest.get().header("X-Test"))
        assertEquals("application/json", capturedRequest.get().header("Accept"))
    }

    @Test
    fun `explicit empty result is successful so caller can fall back`() = runTest {
        val (client, calls) = responseClient(200, "{\"result\":[]}")
        val outcome = DefaultNhentaiRelatedProvider().loadForTest(
            TestHttpSource("https://nhentai.net", client),
            manga("/g/9/"),
        )

        val success = assertInstanceOf(NhentaiRelatedOutcome.Success::class.java, outcome)
        assertTrue(success.manga.isEmpty())
        assertEquals(1, calls.get())
    }

    @Test
    fun `429 returns delta Retry-After without retrying`() = runTest {
        val (client, calls) = responseClient(429, headers = Headers.headersOf("Retry-After", "45"))

        val outcome = DefaultNhentaiRelatedProvider().loadForTest(
            TestHttpSource("https://nhentai.net", client),
            manga("/g/9/"),
        )

        val limited = assertInstanceOf(NhentaiRelatedOutcome.RateLimited::class.java, outcome)
        assertEquals(45.seconds, limited.retryAfter)
        assertEquals(1, calls.get())
    }

    @Test
    fun `429 parses HTTP date Retry-After and invalid value stays null`() = runTest {
        val now = Instant.parse("2026-07-13T00:00:00Z").toEpochMilli()
        val datedClient = responseClient(
            429,
            headers = Headers.headersOf("Retry-After", "Mon, 13 Jul 2026 00:00:30 GMT"),
        ).first
        val invalidClient = responseClient(429, headers = Headers.headersOf("Retry-After", "soon")).first
        val provider = DefaultNhentaiRelatedProvider(nowEpochMillis = { now })

        val dated = provider.loadForTest(TestHttpSource("https://nhentai.net", datedClient), manga("/g/9/"))
        val invalid = provider.loadForTest(TestHttpSource("https://nhentai.net", invalidClient), manga("/g/9/"))

        assertEquals(30.seconds, (dated as NhentaiRelatedOutcome.RateLimited).retryAfter)
        assertNull((invalid as NhentaiRelatedOutcome.RateLimited).retryAfter)
    }

    @Test
    fun `HTTP failures are classified for caller cooldown policy`() = runTest {
        val challenge = DefaultNhentaiRelatedProvider().loadForTest(
            TestHttpSource("https://nhentai.net", responseClient(403).first),
            manga("/g/9/"),
        ) as NhentaiRelatedOutcome.HttpFailure
        val unavailable = DefaultNhentaiRelatedProvider().loadForTest(
            TestHttpSource("https://nhentai.net", responseClient(404).first),
            manga("/g/9/"),
        ) as NhentaiRelatedOutcome.HttpFailure
        val transient = DefaultNhentaiRelatedProvider().loadForTest(
            TestHttpSource("https://nhentai.net", responseClient(503).first),
            manga("/g/9/"),
        ) as NhentaiRelatedOutcome.HttpFailure
        val other = DefaultNhentaiRelatedProvider().loadForTest(
            TestHttpSource("https://nhentai.net", responseClient(418).first),
            manga("/g/9/"),
        ) as NhentaiRelatedOutcome.HttpFailure

        assertEquals(NhentaiHttpFailureClassification.TRANSIENT, challenge.classification)
        assertEquals(NhentaiHttpFailureClassification.CAPABILITY_UNAVAILABLE, unavailable.classification)
        assertEquals(NhentaiHttpFailureClassification.TRANSIENT, transient.classification)
        assertEquals(NhentaiHttpFailureClassification.OTHER, other.classification)
    }

    @Test
    fun `HTML malformed and missing result responses are distinguished`() = runTest {
        val html = DefaultNhentaiRelatedProvider().loadForTest(
            TestHttpSource(
                "https://nhentai.net",
                responseClient(200, "<html>challenge</html>", contentType = "text/html").first,
            ),
            manga("/g/9/"),
        )
        val malformed = DefaultNhentaiRelatedProvider().loadForTest(
            TestHttpSource("https://nhentai.net", responseClient(200, "{broken").first),
            manga("/g/9/"),
        )
        val missing = DefaultNhentaiRelatedProvider().loadForTest(
            TestHttpSource("https://nhentai.net", responseClient(200, "{\"other\":[]}").first),
            manga("/g/9/"),
        )

        assertEquals(
            NhentaiInvalidResponseReason.NON_JSON,
            (html as NhentaiRelatedOutcome.InvalidResponse).reason,
        )
        assertEquals(
            NhentaiInvalidResponseReason.MALFORMED_JSON,
            (malformed as NhentaiRelatedOutcome.InvalidResponse).reason,
        )
        assertEquals(
            NhentaiInvalidResponseReason.MISSING_RESULT,
            (missing as NhentaiRelatedOutcome.InvalidResponse).reason,
        )
    }

    @Test
    fun `network failure is returned without retry`() = runTest {
        val calls = AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor {
            calls.incrementAndGet()
            throw IOException("offline")
        }.build()

        val outcome = DefaultNhentaiRelatedProvider().loadForTest(
            TestHttpSource("https://nhentai.net", client),
            manga("/g/9/"),
        )

        assertInstanceOf(NhentaiRelatedOutcome.NetworkFailure::class.java, outcome)
        assertEquals(1, calls.get())
    }

    private class TestHttpSource(
        override val baseUrl: String,
        override val client: OkHttpClient,
    ) : HttpSource() {
        override val name: String = "nHentai"
        override val lang: String = "en"
        override val supportsLatest: Boolean = false
        val detailCalls = AtomicInteger()

        override fun headersBuilder(): Headers.Builder {
            return Headers.Builder().add("X-Test", "test-value")
        }

        override suspend fun getMangaUpdate(
            manga: SManga,
            chapters: List<SChapter>,
            fetchDetails: Boolean,
            fetchChapters: Boolean,
        ): SMangaUpdate {
            detailCalls.incrementAndGet()
            return SMangaUpdate(manga, chapters)
        }
    }

    private data class ResponseClient(
        val first: OkHttpClient,
        val second: AtomicInteger,
        val third: AtomicReference<Request>,
    )

    private fun responseClient(
        code: Int,
        body: String = "",
        contentType: String? = "application/json",
        headers: Headers = Headers.Builder().build(),
    ): ResponseClient {
        val calls = AtomicInteger()
        val capturedRequest = AtomicReference<Request>()
        val interceptor = Interceptor { chain ->
            calls.incrementAndGet()
            capturedRequest.set(chain.request())
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("test")
                .headers(headers)
                .body(body.toResponseBody(contentType?.toMediaType()))
                .build()
        }
        return ResponseClient(OkHttpClient.Builder().addInterceptor(interceptor).build(), calls, capturedRequest)
    }

    private fun manga(url: String): SManga = SManga.create().apply {
        this.url = url
        title = "Current"
    }

    private fun NhentaiRelatedProvider.supportsForTest(source: Source, manga: SManga): Boolean =
        resolve(source, manga) != null

    private suspend fun NhentaiRelatedProvider.loadForTest(
        source: Source,
        manga: SManga,
    ): NhentaiRelatedOutcome = resolve(source, manga)?.let { load(it) } ?: NhentaiRelatedOutcome.Unsupported

    private data object NonHttpSource : Source {
        override val id: Long = 1
        override val name: String = "Not HTTP"
        override val supportsLatest: Boolean = false

        override suspend fun getPopularManga(page: Int): MangasPage = error("not called")

        override suspend fun getLatestUpdates(page: Int): MangasPage = error("not called")

        override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage =
            error("not called")

        override suspend fun getMangaUpdate(
            manga: SManga,
            chapters: List<SChapter>,
            fetchDetails: Boolean,
            fetchChapters: Boolean,
        ): SMangaUpdate = error("not called")

        override suspend fun getPageList(chapter: SChapter): List<Page> = error("not called")
    }
}
