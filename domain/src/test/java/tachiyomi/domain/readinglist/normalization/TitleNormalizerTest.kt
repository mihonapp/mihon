package tachiyomi.domain.readinglist.normalization

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
class TitleNormalizerTest {

    @Test
    fun `normalizes punctuation case accents and common symbols`() {
        val normalized = TitleNormalizer.normalize("  POKÉMON: Black & White — Adventures!  ")

        normalized.canonical shouldBe "pokemon black and white adventures"
        normalized.base shouldBe normalized.canonical
        normalized.tokens shouldContainExactly listOf("pokemon", "black", "and", "white", "adventures")
    }

    @Test
    fun `removes apostrophes without splitting possessives`() {
        TitleNormalizer.normalize("Marvel’s Voices").base shouldBe "marvels voices"
    }

    @Test
    fun `extracts trailing year and numeric volume metadata`() {
        val normalized = TitleNormalizer.normalize("Batman, Vol. 3 (2016)")

        normalized.canonical shouldBe "batman vol 3 2016"
        normalized.base shouldBe "batman"
        normalized.year shouldBe 2016
        normalized.volume shouldBe 3
    }

    @Test
    fun `extracts a roman numeral volume before a trailing year`() {
        val normalized = TitleNormalizer.normalize("The Flash (2016) Volume IV")

        normalized.base shouldBe "the flash"
        normalized.articlelessBase shouldBe "flash"
        normalized.year shouldBe 2016
        normalized.volume shouldBe 4
    }

    @Test
    fun `keeps bare years and numeric titles as title content`() {
        val normalized = TitleNormalizer.normalize("52 2006")

        normalized.base shouldBe "52 2006"
        normalized.year shouldBe null
        normalized.volume shouldBe null
    }

    @Test
    fun `leading articles are comparison variants rather than destructive normalization`() {
        val withArticle = TitleNormalizer.normalize("The Amazing Spider-Man")
        val withoutArticle = TitleNormalizer.normalize("Amazing Spider Man")

        withArticle.base shouldBe "the amazing spider man"
        withArticle.articlelessBase shouldBe "amazing spider man"
        withArticle.isEquivalentTo(withoutArticle) shouldBe true
    }

    @Test
    fun `edition metadata can match a title without the edition suffix`() {
        val edition = TitleNormalizer.normalize("Batman (2016)")
        val plain = TitleNormalizer.normalize("Batman")

        edition.isEquivalentTo(plain) shouldBe true
    }

    @Test
    fun `blank titles are deterministic but not usable for matching`() {
        val first = TitleNormalizer.normalize(" ")
        val second = TitleNormalizer.normalize("")

        first shouldBe second
        first.isUsable shouldBe false
        first.isEquivalentTo(second) shouldBe false
    }
}
