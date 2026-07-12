package eu.kanade.tachiyomi.ui.readinglist

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
class CblDocumentReaderTest {

    private val xml = "<ReadingList><Name>Fixture</Name><Books /></ReadingList>"

    @Test
    fun `decodes UTF-8 with and without BOM`() {
        decodeCblDocument(xml.toByteArray(Charsets.UTF_8)) shouldBe xml
        decodeCblDocument(
            byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
                xml.toByteArray(Charsets.UTF_8),
        ) shouldBe xml
    }

    @Test
    fun `decodes UTF-16 little endian with and without BOM`() {
        val encoded = xml.toByteArray(Charsets.UTF_16LE)

        decodeCblDocument(encoded) shouldBe xml
        decodeCblDocument(
            byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + encoded,
        ) shouldBe xml
    }

    @Test
    fun `decodes UTF-16 big endian with and without BOM`() {
        val encoded = xml.toByteArray(Charsets.UTF_16BE)

        decodeCblDocument(encoded) shouldBe xml
        decodeCblDocument(
            byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + encoded,
        ) shouldBe xml
    }

    @Test
    fun `empty documents decode deterministically`() {
        decodeCblDocument(byteArrayOf()) shouldBe ""
    }
}
