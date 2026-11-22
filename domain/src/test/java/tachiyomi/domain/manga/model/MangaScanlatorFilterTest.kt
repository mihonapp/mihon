package tachiyomi.domain.manga.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
class MangaScanlatorFilterTest {

    private val excluding = Manga.create()
    private val including = Manga.create().copy(chapterFlags = Manga.CHAPTER_SCANLATOR_INCLUDE)

    @Test
    fun `nothing is hidden while no scanlator is checked`() {
        excluding.hidesScanlator("Group", emptySet()) shouldBe false
        including.hidesScanlator("Group", emptySet()) shouldBe false
        including.hidesScanlator(null, emptySet()) shouldBe false
    }

    @Test
    fun `exclude mode hides only the checked scanlators`() {
        excluding.hidesScanlator("Group", setOf("Group")) shouldBe true
        excluding.hidesScanlator("Other", setOf("Group")) shouldBe false
        excluding.hidesScanlator(null, setOf("Group")) shouldBe false
    }

    @Test
    fun `include mode hides everything but the checked scanlators`() {
        including.hidesScanlator("Group", setOf("Group")) shouldBe false
        including.hidesScanlator("Other", setOf("Group")) shouldBe true
        including.hidesScanlator(null, setOf("Group")) shouldBe true
        including.hidesScanlator("", setOf("Group")) shouldBe true
    }
}
