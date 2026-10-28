package eu.kanade.tachiyomi.ui.reader.loader

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class HttpPageLoaderTest {

    @Test
    fun `reads the page index from downloaded page file names`() {
        pageIndexFromFileName("001.jpg") shouldBe 0
        pageIndexFromFileName("010.png") shouldBe 9
        pageIndexFromFileName("1234.webp") shouldBe 1233
    }

    @Test
    fun `ignores files that are not whole page images`() {
        pageIndexFromFileName("001.tmp") shouldBe null
        pageIndexFromFileName("001__001.jpg") shouldBe null
        pageIndexFromFileName("ComicInfo.xml") shouldBe null
        pageIndexFromFileName(".nomedia") shouldBe null
    }
}
