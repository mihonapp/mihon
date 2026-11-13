package tachiyomi.translation.ocr

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class OcrModelStoreCharsetTest {
    @Test
    fun `yml parser keeps quote, space and ideographic-space entries`() {
        // PP-OCRv6 dictionaries really contain these; losing any of them shifts every later
        // character and silently kills the space class.
        val charset = OcrModelStore.parseYmlCharset(
            listOf(
                "  - a",
                "  - '\"'",
                "  - ''''",
                "  - ' '",
                "  - \u3000",
                "  - 'x''y'",
                "  - '🛅'",
            ),
        )
        charset shouldBe listOf("a", "\"", "'", " ", "\u3000", "x'y", "🛅")
    }

    @Test
    fun `unquoted entries keep their inner spacing`() {
        val charset = OcrModelStore.parseYmlCharset(listOf("  - hello world", "  - ab"))
        charset shouldBe listOf("hello world", "ab")
    }
}
