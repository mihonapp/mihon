package eu.kanade.tachiyomi.data.translation.overlay

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OverlayResponseParserDroppedTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun `boxes that are left out are reported with a reason`() {
        val dropped = mutableListOf<String>()
        val answer = """
            {"boxes":[
              {"box_2d":[10,10,50,90],"kind":"bubble","source_text":"HELLO","text":"مرحبا"},
              {"box_2d":[10,10,50],"kind":"bubble","source_text":"BAD","text":"سيء"},
              {"box_2d":[10,10,50,90],"kind":"bubble","source_text":"NO TEXT","text":""},
              {"box_2d":[10,10,10,90],"kind":"caption","source_text":"FLAT","text":"مسطح"}
            ]}
        """.trimIndent()

        val boxes = OverlayResponseParser.parseAnswer(answer, json) { dropped += it }

        assertEquals(1, boxes.size)
        assertEquals(3, dropped.size)
        assertTrue(dropped[0].contains("box_2d is not four numbers"))
        assertTrue(dropped[1].contains("no translated text"))
        assertTrue(dropped[2].contains("no area"))
    }

    @Test
    fun `a complete answer reports nothing`() {
        val dropped = mutableListOf<String>()
        val answer = """{"boxes":[{"box_2d":[10,10,50,90],"kind":"bubble","source_text":"HI","text":"مرحبا"}]}"""

        val boxes = OverlayResponseParser.parseAnswer(answer, json) { dropped += it }

        assertEquals(1, boxes.size)
        assertTrue(dropped.isEmpty())
    }
}
