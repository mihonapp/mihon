package eu.kanade.tachiyomi.data.translation.ocr

import io.mockk.mockk
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LocalOcrClientTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val client = LocalOcrClient(
        networkHelper = mockk(relaxed = true),
        localOcrEngine = mockk(relaxed = true),
        json = json,
    )

    @Test
    fun `parses clean JSON array of translations`() {
        val input = """
            [
              {"id": 0, "text": "مرحباً"},
              {"id": 1, "text": "كيف حالك؟"}
            ]
        """.trimIndent()

        val result = client.parseTranslations(input)

        assertEquals(2, result.size)
        assertEquals("مرحباً", result[0])
        assertEquals("كيف حالك؟", result[1])
    }

    @Test
    fun `parses wrapped JSON object with list`() {
        val input = """
            {
              "translations": [
                {"id": 0, "text": "لا يمكن!"},
                {"id": 5, "text": "اهرب الآن!"}
              ]
            }
        """.trimIndent()

        val result = client.parseTranslations(input)

        assertEquals(2, result.size)
        assertEquals("لا يمكن!", result[0])
        assertEquals("اهرب الآن!", result[5])
    }

    @Test
    fun `parses markdown fenced code block`() {
        val input = """
            ```json
            [
              {"id": 2, "text": "ما هذا الشيء؟"}
            ]
            ```
        """.trimIndent()

        val result = client.parseTranslations(input)

        assertEquals(1, result.size)
        assertEquals("ما هذا الشيء؟", result[2])
    }

    @Test
    fun `parses LLM answer with conversational text around JSON`() {
        val input = """
            Here are the translations requested:
            [
              {"id": 0, "text": "أنا بخير."},
              {"id": 1, "text": "شكراً جزيلاً!"}
            ]
            Hope this helps with your manga!
        """.trimIndent()

        val result = client.parseTranslations(input)

        assertEquals(2, result.size)
        assertEquals("أنا بخير.", result[0])
        assertEquals("شكراً جزيلاً!", result[1])
    }

    @Test
    fun `handles completely broken output gracefully without throwing`() {
        val input = "Sorry, I cannot translate this content."
        val result = client.parseTranslations(input)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `fallbackTranslateWord uses natural colloquial baladi words`() {
        assertEquals("شو!", client.fallbackTranslateWord("What!", "Arabic", eu.kanade.domain.translation.TranslationOptions.ARABIC_STYLE_BALADI))
        assertEquals("ليش؟", client.fallbackTranslateWord("Why?", "Arabic", eu.kanade.domain.translation.TranslationOptions.ARABIC_STYLE_BALADI))
        assertEquals("مين؟", client.fallbackTranslateWord("Who?", "Arabic", eu.kanade.domain.translation.TranslationOptions.ARABIC_STYLE_BALADI))
        assertEquals("وين؟", client.fallbackTranslateWord("Where?", "Arabic", eu.kanade.domain.translation.TranslationOptions.ARABIC_STYLE_BALADI))
        assertEquals("استنى!", client.fallbackTranslateWord("Wait!", "Arabic", eu.kanade.domain.translation.TranslationOptions.ARABIC_STYLE_BALADI))
        assertEquals("وقف!", client.fallbackTranslateWord("Stop!", "Arabic", eu.kanade.domain.translation.TranslationOptions.ARABIC_STYLE_BALADI))
        assertEquals("شوف!", client.fallbackTranslateWord("Look!", "Arabic", eu.kanade.domain.translation.TranslationOptions.ARABIC_STYLE_BALADI))
        assertEquals("تمام", client.fallbackTranslateWord("OK", "Arabic", eu.kanade.domain.translation.TranslationOptions.ARABIC_STYLE_BALADI))
        assertEquals("أكيد", client.fallbackTranslateWord("Sure", "Arabic", eu.kanade.domain.translation.TranslationOptions.ARABIC_STYLE_BALADI))
    }

    @Test
    fun `fallbackTranslateWord uses formal standard words when style is modern`() {
        assertEquals("ماذا!", client.fallbackTranslateWord("What!", "Arabic", eu.kanade.domain.translation.TranslationOptions.ARABIC_STYLE_MODERN))
        assertEquals("لماذا؟", client.fallbackTranslateWord("Why?", "Arabic", eu.kanade.domain.translation.TranslationOptions.ARABIC_STYLE_MODERN))
        assertEquals("من؟", client.fallbackTranslateWord("Who?", "Arabic", eu.kanade.domain.translation.TranslationOptions.ARABIC_STYLE_MODERN))
        assertEquals("أين؟", client.fallbackTranslateWord("Where?", "Arabic", eu.kanade.domain.translation.TranslationOptions.ARABIC_STYLE_MODERN))
        assertEquals("انتظر!", client.fallbackTranslateWord("Wait!", "Arabic", eu.kanade.domain.translation.TranslationOptions.ARABIC_STYLE_MODERN))
        assertEquals("توقف!", client.fallbackTranslateWord("Stop!", "Arabic", eu.kanade.domain.translation.TranslationOptions.ARABIC_STYLE_MODERN))
        assertEquals("انظر!", client.fallbackTranslateWord("Look!", "Arabic", eu.kanade.domain.translation.TranslationOptions.ARABIC_STYLE_MODERN))
        assertEquals("حسناً", client.fallbackTranslateWord("OK", "Arabic", eu.kanade.domain.translation.TranslationOptions.ARABIC_STYLE_MODERN))
    }
}
