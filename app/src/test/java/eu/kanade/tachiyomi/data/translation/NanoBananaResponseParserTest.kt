package eu.kanade.tachiyomi.data.translation

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Base64

class NanoBananaResponseParserTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val imageBytes = byteArrayOf(1, 2, 3, 4, 5, 127, -128, -1)
    private val imageBase64: String = Base64.getEncoder().encodeToString(imageBytes)

    private fun success(parts: String, finishReason: String = "STOP") =
        """{"candidates":[{"content":{"parts":[$parts]},"finishReason":"$finishReason"}]}"""

    // region parseImage

    @Test
    fun `image part is decoded`() {
        val body = success(
            """{"text":"Here you go"},{"inlineData":{"mimeType":"image/png","data":"$imageBase64"}}""",
        )

        val image = NanoBananaResponseParser.parseImage(body, json)

        assertArrayEquals(imageBytes, image.bytes)
        assertEquals("image/png", image.mimeType)
        assertEquals("png", image.extension)
    }

    @Test
    fun `snake case inline_data is accepted`() {
        val body = success("""{"inline_data":{"mime_type":"image/jpeg","data":"$imageBase64"}}""")

        val image = NanoBananaResponseParser.parseImage(body, json)

        assertArrayEquals(imageBytes, image.bytes)
        assertEquals("jpg", image.extension)
    }

    @Test
    fun `final image is preferred over thought images`() {
        val thought = Base64.getEncoder().encodeToString(byteArrayOf(9, 9, 9))
        val body = success(
            """{"thought":true,"inlineData":{"mimeType":"image/png","data":"$thought"}},""" +
                """{"inlineData":{"mimeType":"image/webp","data":"$imageBase64"}}""",
        )

        val image = NanoBananaResponseParser.parseImage(body, json)

        assertArrayEquals(imageBytes, image.bytes)
        assertEquals("webp", image.extension)
    }

    @Test
    fun `text only answer throws NoImageReturned with the text`() {
        val body = success("""{"text":"I cannot do that"}""")

        val error = assertInstanceOf(
            TranslationException.NoImageReturned::class.java,
            runCatching { NanoBananaResponseParser.parseImage(body, json) }.exceptionOrNull(),
        )

        assertTrue(error.message!!.contains("I cannot do that"))
        assertEquals(1, error.maxRetries)
        assertFalse(error.isFatal)
    }

    @Test
    fun `missing candidates throws NoImageReturned`() {
        assertInstanceOf(
            TranslationException.NoImageReturned::class.java,
            runCatching { NanoBananaResponseParser.parseImage("{}", json) }.exceptionOrNull(),
        )
    }

    @Test
    fun `prompt feedback block reason throws ContentBlocked`() {
        val body = """{"promptFeedback":{"blockReason":"PROHIBITED_CONTENT"}}"""

        val error = assertInstanceOf(
            TranslationException.ContentBlocked::class.java,
            runCatching { NanoBananaResponseParser.parseImage(body, json) }.exceptionOrNull(),
        )

        assertEquals("PROHIBITED_CONTENT", error.reason)
    }

    @Test
    fun `safety finish reason without image throws ContentBlocked`() {
        val body = """{"candidates":[{"finishReason":"IMAGE_SAFETY"}]}"""

        val error = assertInstanceOf(
            TranslationException.ContentBlocked::class.java,
            runCatching { NanoBananaResponseParser.parseImage(body, json) }.exceptionOrNull(),
        )

        assertEquals("IMAGE_SAFETY", error.reason)
    }

    @Test
    fun `invalid base64 throws NoImageReturned`() {
        val body = success("""{"inlineData":{"mimeType":"image/png","data":"A"}}""")

        assertInstanceOf(
            TranslationException.NoImageReturned::class.java,
            runCatching { NanoBananaResponseParser.parseImage(body, json) }.exceptionOrNull(),
        )
    }

    @Test
    fun `garbage body throws NoImageReturned`() {
        assertInstanceOf(
            TranslationException.NoImageReturned::class.java,
            runCatching { NanoBananaResponseParser.parseImage("<html>nope</html>", json) }.exceptionOrNull(),
        )
    }

    // endregion

    // region parseError

    private fun apiError(code: Int, message: String, extra: String = "", header: String? = null) =
        NanoBananaResponseParser.parseError(
            code,
            """{"error":{"code":$code,"message":"$message","status":"X"$extra}}""",
            json,
            header,
        )

    @Test
    fun `400 with invalid key message is InvalidApiKey`() {
        val e = apiError(400, "API key not valid. Please pass a valid API key.")
        assertInstanceOf(TranslationException.InvalidApiKey::class.java, e)
        assertTrue(e.isFatal)
    }

    @Test
    fun `other 400 is a fatal RequestRejected`() {
        val e = apiError(400, "User location is not supported for the API use.")
        assertInstanceOf(TranslationException.RequestRejected::class.java, e)
        assertTrue(e.isFatal)
    }

    @Test
    fun `401 and plain 403 are InvalidApiKey`() {
        assertInstanceOf(TranslationException.InvalidApiKey::class.java, apiError(401, "Unauthorized"))
        assertInstanceOf(TranslationException.InvalidApiKey::class.java, apiError(403, "Permission denied"))
    }

    @Test
    fun `403 mentioning billing is QuotaExceeded`() {
        assertInstanceOf(TranslationException.QuotaExceeded::class.java, apiError(403, "Billing is not enabled"))
    }

    @Test
    fun `404 is ModelNotFound`() {
        val e = apiError(404, "models/foo is not found")
        assertInstanceOf(TranslationException.ModelNotFound::class.java, e)
        assertTrue(e.isFatal)
    }

    @Test
    fun `429 with per day quota is fatal QuotaExceeded`() {
        val e = apiError(
            429,
            "You exceeded your current quota",
            ""","details":[{"violations":[{"quotaId":"GenerateRequestsPerDayPerProjectPerModel-FreeTier"}]}]""",
        )
        assertInstanceOf(TranslationException.QuotaExceeded::class.java, e)
        assertTrue(e.isFatal)
        assertEquals(0, e.maxRetries)
    }

    @Test
    fun `429 with zero limit is QuotaExceeded`() {
        assertInstanceOf(
            TranslationException.QuotaExceeded::class.java,
            apiError(429, "Quota exceeded for metric, limit: 0"),
        )
    }

    @Test
    fun `429 per minute is retryable RateLimited with retry delay`() {
        val e = apiError(
            429,
            "You exceeded your current quota, please check your plan and billing details.",
            ""","details":[{"violations":[{"quotaId":"GenerateRequestsPerMinutePerProjectPerModel"}]},""" +
                """{"retryDelay":"34s"}]""",
        )

        val rateLimited = assertInstanceOf(TranslationException.RateLimited::class.java, e)
        assertEquals(34_000L, rateLimited.retryAfterMillis)
        assertEquals(3, rateLimited.maxRetries)
        assertFalse(rateLimited.isFatal)
    }

    @Test
    fun `ambiguous 429 defaults to RateLimited and Retry-After header is honoured`() {
        val e = apiError(429, "Resource exhausted", header = "12")

        val rateLimited = assertInstanceOf(TranslationException.RateLimited::class.java, e)
        assertEquals(12_000L, rateLimited.retryAfterMillis)
    }

    @Test
    fun `retry delay is capped at one minute`() {
        val e = apiError(429, "slow down", ""","details":[{"retryDelay":"900s"}]""")
        assertEquals(60_000L, (e as TranslationException.RateLimited).retryAfterMillis)
    }

    @Test
    fun `5xx is retryable ServerError`() {
        val e = NanoBananaResponseParser.parseError(503, "<html>unavailable</html>", json)
        assertInstanceOf(TranslationException.ServerError::class.java, e)
        assertEquals(3, e.maxRetries)
        assertFalse(e.isFatal)
    }

    @Test
    fun `unknown status with non json body is fatal RequestRejected without message`() {
        val e = NanoBananaResponseParser.parseError(418, "teapot", json)
        val rejected = assertInstanceOf(TranslationException.RequestRejected::class.java, e)
        assertNull(rejected.detail)
        assertTrue(rejected.isFatal)
    }

    // endregion

    // region request building

    @Test
    fun `prompt contains the target language`() {
        val prompt = NanoBananaClient.buildPrompt("Arabic")
        assertTrue(prompt.contains("into Arabic."))
        assertFalse(prompt.contains("{language}"))
    }

    @Test
    fun `request contains prompt and inline image and requests image output`() {
        val request = NanoBananaClient.buildRequest(imageBytes, "image/jpeg", "French")
        val encoded = json.encodeToString(request)

        assertTrue(encoded.contains("\"inline_data\""))
        assertTrue(encoded.contains("\"mime_type\":\"image/jpeg\""))
        assertTrue(encoded.contains(imageBase64))
        assertTrue(encoded.contains("\"responseModalities\":[\"TEXT\",\"IMAGE\"]"))
        assertTrue(encoded.contains("into French."))
    }

    @Test
    fun `backoff grows exponentially and honours the retry hint within bounds`() {
        assertEquals(2_000L, NanoBananaClient.backoffMillis(1, null))
        assertEquals(4_000L, NanoBananaClient.backoffMillis(2, null))
        assertEquals(8_000L, NanoBananaClient.backoffMillis(3, null))
        assertEquals(30_000L, NanoBananaClient.backoffMillis(1, 30_000L))
        assertEquals(60_000L, NanoBananaClient.backoffMillis(1, 500_000L))
    }

    // endregion
}
