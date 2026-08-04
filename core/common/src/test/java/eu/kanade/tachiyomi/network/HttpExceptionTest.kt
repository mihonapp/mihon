package eu.kanade.tachiyomi.network

import io.kotest.matchers.shouldBe
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.jupiter.api.Test

class HttpExceptionTest {

    @Test
    fun `one argument constructor remains available`() {
        val exception = HttpException(404)

        exception.code shouldBe 404
        exception.retryAfter shouldBe null
        exception.message shouldBe "HTTP error 404"
    }

    @Test
    fun `response conversion preserves Retry-After`() {
        val response = Response.Builder()
            .request(Request.Builder().url("https://example.org/").build())
            .protocol(Protocol.HTTP_1_1)
            .code(429)
            .message("Too Many Requests")
            .header("Retry-After", "120")
            .build()

        val exception = response.toHttpException()

        exception.code shouldBe 429
        exception.retryAfter shouldBe "120"
    }
}
