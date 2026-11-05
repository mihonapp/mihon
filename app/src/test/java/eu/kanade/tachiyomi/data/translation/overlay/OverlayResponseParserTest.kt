package eu.kanade.tachiyomi.data.translation.overlay

import eu.kanade.tachiyomi.data.translation.TranslationException
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OverlayResponseParserTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private fun answer(text: String, finishReason: String = "STOP"): String {
        val escaped = text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        return """{"candidates":[{"content":{"parts":[{"text":"$escaped"}]},"finishReason":"$finishReason"}]}"""
    }

    @Test
    fun `overlay prompt requests tight glyph bounds away from faces and artwork`() {
        val prompt = TextOverlayClient.buildPrompt("Arabic")

        assertTrue(prompt.contains("Enclose all visible lettering"))
        assertTrue(prompt.contains("Do NOT box the whole bubble, panel, sign, or empty space"))
        assertTrue(prompt.contains("Never include a face, hair, body, or artwork"))
        assertTrue(prompt.contains("crops from tall pages"))
        assertTrue(prompt.contains("cut off by crop edges"))
        assertTrue(prompt.contains("second top-to-bottom scan"))
        assertTrue(prompt.contains("exact visible source lettering"))
        assertTrue(prompt.contains("Every readable phrase must have one entry"))
        assertTrue(prompt.contains("source_text"))
        assertTrue(prompt.contains("outline or shadow"))
    }

    @Test
    fun `valid object is parsed`() {
        val boxes = OverlayResponseParser.parse(
            answer(
                """{"boxes":[{"box_2d":[100,200,300,400],"kind":"bubble","source_text":"Hello","text":"مرحبا"}]}""",
            ),
            json,
        )

        assertEquals(listOf(TextBox(100, 200, 300, 400, BoxKind.BUBBLE, "مرحبا", "Hello")), boxes)
    }

    @Test
    fun `valid partial json is rejected when model hits token limit`() {
        assertThrows(TranslationException.OutputTruncated::class.java) {
            OverlayResponseParser.parse(
                answer(
                    """{"boxes":[{"box_2d":[100,200,300,400],"kind":"bubble","text":"Partial"}]}""",
                    finishReason = "MAX_TOKENS",
                ),
                json,
            )
        }
    }

    @Test
    fun `fenced json is parsed`() {
        val boxes = OverlayResponseParser.parse(
            answer("```json\n{\"boxes\":[{\"box_2d\":[1,2,3,4],\"kind\":\"caption\",\"text\":\"A\"}]}\n```"),
            json,
        )

        assertEquals(1, boxes.size)
        assertEquals(BoxKind.CAPTION, boxes[0].kind)
    }

    @Test
    fun `bare array is accepted`() {
        val boxes = OverlayResponseParser.parse(
            answer("""[{"box_2d":[1,2,3,4],"kind":"sfx","text":"BOOM"}]"""),
            json,
        )

        assertEquals(BoxKind.SFX, boxes.single().kind)
    }

    @Test
    fun `coordinates are clamped and swapped`() {
        val boxes = OverlayResponseParser.parse(
            answer("""{"boxes":[{"box_2d":[900,1200,-50,100],"kind":"bubble","text":"x"}]}"""),
            json,
        )

        val box = boxes.single()
        assertEquals(0, box.yMin)
        assertEquals(900, box.yMax)
        assertEquals(100, box.xMin)
        assertEquals(1000, box.xMax)
    }

    @Test
    fun `malformed boxes are dropped`() {
        val boxes = OverlayResponseParser.parse(
            answer(
                """{"boxes":[
                {"box_2d":[1,2,3],"kind":"bubble","text":"short"},
                {"box_2d":[1,2,3,4],"kind":"bubble","text":"  "},
                {"kind":"bubble","text":"no box"},
                {"box_2d":[10,20,30,40],"kind":"bubble","text":"ok"}]}""",
            ),
            json,
        )

        assertEquals(listOf("ok"), boxes.map { it.text })
    }

    @Test
    fun `empty boxes means no text`() {
        assertTrue(OverlayResponseParser.parse(answer("""{"boxes":[]}"""), json).isEmpty())
    }

    @Test
    fun `unknown kind falls back to bubble`() {
        val boxes = OverlayResponseParser.parse(
            answer("""{"boxes":[{"box_2d":[1,2,3,4],"kind":"thought","text":"hm"}]}"""),
            json,
        )

        assertEquals(BoxKind.BUBBLE, boxes.single().kind)
    }

    @Test
    fun `block reason throws ContentBlocked`() {
        assertThrows(TranslationException.ContentBlocked::class.java) {
            OverlayResponseParser.parse("""{"promptFeedback":{"blockReason":"SAFETY"}}""", json)
        }
    }

    @Test
    fun `safety finish reason with empty answer throws ContentBlocked`() {
        assertThrows(TranslationException.ContentBlocked::class.java) {
            OverlayResponseParser.parse("""{"candidates":[{"finishReason":"SAFETY"}]}""", json)
        }
    }

    @Test
    fun `garbage throws UnreadableAnswer`() {
        assertThrows(TranslationException.UnreadableAnswer::class.java) {
            OverlayResponseParser.parse(answer("Sorry, I can't do that."), json)
        }
        assertThrows(TranslationException.UnreadableAnswer::class.java) {
            OverlayResponseParser.parse("not json at all", json)
        }
    }
}
