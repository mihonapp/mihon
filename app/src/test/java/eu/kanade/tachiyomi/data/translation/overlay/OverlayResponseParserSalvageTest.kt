package eu.kanade.tachiyomi.data.translation.overlay

import eu.kanade.tachiyomi.data.translation.TranslationException
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OverlayResponseParserSalvageTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun `unescaped quotes in a translation do not cost the whole answer`() {
        val notes = mutableListOf<String>()
        val answer = """
            {"boxes":[
              {"box_2d":[10,20,50,90],"kind":"bubble","source_text":"HE SAID HELLO",
               "text":"قال "مرحبا" للجميع"},
              {"box_2d":[100,20,140,90],"kind":"caption","source_text":"SECOND","text":"الثاني"}
            ]}
        """.trimIndent()

        val boxes = OverlayResponseParser.parseAnswer(answer, json) { notes += it }

        assertEquals(2, boxes.size)
        assertEquals("قال \"مرحبا\" للجميع", boxes[0].text)
        assertEquals("HE SAID HELLO", boxes[0].sourceText)
        assertEquals(BoxKind.CAPTION, boxes[1].kind)
        assertTrue(notes.any { it.contains("not valid JSON") })
    }

    @Test
    fun `a raw line break inside a translation is recovered`() {
        val answer = "{\"boxes\":[{\"box_2d\":[10,20,50,90],\"kind\":\"bubble\"," +
            "\"source_text\":\"TWO\\nLINES\",\"text\":\"سطر\nثان\"}]}"

        val boxes = OverlayResponseParser.parseAnswer(answer, json)

        assertEquals(1, boxes.size)
        assertTrue(boxes.single().text.contains("ثان"))
    }

    @Test
    fun `coordinates wrapped in a second array are accepted`() {
        val answer = """{"boxes":[{"box_2d":[[10,20,50,90]],"kind":"bubble","source_text":"HI","text":"مرحبا"}]}"""

        val boxes = OverlayResponseParser.parseAnswer(answer, json)

        assertEquals(1, boxes.size)
        assertEquals(10, boxes.single().yMin)
        assertEquals(90, boxes.single().xMax)
    }

    @Test
    fun `a box that is cut off is skipped and the others are kept`() {
        val notes = mutableListOf<String>()
        val answer = """{"boxes":[{"box_2d":[10,20,50,90],"kind":"bubble","source_text":"A","text":"واحد"},""" +
            """{"box_2d":[60,20,90,90],"kind":"bubble","source_text":"B","text":"مقطو"""

        val boxes = OverlayResponseParser.parseAnswer(answer, json) { notes += it }

        assertEquals(listOf("واحد"), boxes.map { it.text })
        assertTrue(notes.any { it.contains("cut off") })
    }

    @Test
    fun `an answer without any box still fails with the reason`() {
        val error = assertThrows(TranslationException.UnreadableAnswer::class.java) {
            OverlayResponseParser.parseAnswer("{oops}", json)
        }

        assertTrue(error.message.orEmpty().contains("Invalid JSON"))
    }

    @Test
    fun `an answer cut off in the middle of a box keeps the complete boxes`() {
        val notes = mutableListOf<String>()
        val answer = "{\"boxes\":[" +
            "{\"box_2d\":[10,20,50,90],\"kind\":\"bubble\",\"source_text\":\"ONE\",\"text\":\"واحد\"}," +
            "{\"box_2d\":[100,20,140,90],\"kind\":\"bubble\",\"source_text\":\"TWO\",\"text\":\"اثنان\"}," +
            "{\"box_2d\":[200,20,240,90],\"kind\":\"bubble\",\"source_text\":\"THR"

        val boxes = OverlayResponseParser.parseAnswer(answer, json) { notes += it }

        assertEquals(listOf("واحد", "اثنان"), boxes.map { it.text })
        assertTrue(notes.any { it.contains("cut off") })
    }

    @Test
    fun `repairing a bare array that is cut off closes the array`() {
        val repaired = OverlayResponseParser.repairTruncated(
            "[{\"box_2d\":[1,2,3,4],\"text\":\"a\"},{\"box_2d\":[5,6",
        )

        assertEquals("[{\"box_2d\":[1,2,3,4],\"text\":\"a\"}]", repaired)
    }

    @Test
    fun `braces inside a translation do not fool the repair`() {
        val repaired = OverlayResponseParser.repairTruncated(
            "{\"boxes\":[{\"box_2d\":[1,2,3,4],\"text\":\"a } b\"},{\"box_2d\":[5",
        )

        assertEquals("{\"boxes\":[{\"box_2d\":[1,2,3,4],\"text\":\"a } b\"}]}", repaired)
    }

    @Test
    fun `nothing to repair when no box is complete`() {
        assertEquals(null, OverlayResponseParser.repairTruncated("{\"boxes\":[{\"box_2d\":[1,2"))
    }

    @Test
    fun `the request asks for the box schema`() {
        val request = TextOverlayClient.buildRequest(byteArrayOf(1), "image/jpeg", "Arabic")

        assertEquals(TextOverlayClient.RESPONSE_SCHEMA, request.generationConfig.responseSchema)
    }

    @Test
    fun `retries ask with more randomness up to a limit`() {
        assertEquals(0.1, TextOverlayClient.temperatureFor(0), 1e-9)
        assertTrue(TextOverlayClient.temperatureFor(1) > TextOverlayClient.temperatureFor(0))
        assertEquals(0.7, TextOverlayClient.temperatureFor(10), 1e-9)
    }
}
