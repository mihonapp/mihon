package tachiyomi.domain.reader

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReadingModeAutoTagInputTest {

    @Test
    fun parseTagInput_preservesMultiWordSegments() {
        assertEquals(
            listOf("web comic", "martial arts"),
            parseTagInput("web comic; martial arts"),
        )
    }

    @Test
    fun parseTagInput_splitsNewlines() {
        assertEquals(
            listOf("a", "b"),
            parseTagInput("a\nb"),
        )
    }
}
