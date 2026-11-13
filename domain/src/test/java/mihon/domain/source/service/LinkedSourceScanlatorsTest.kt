package mihon.domain.source.service

import eu.kanade.tachiyomi.source.model.SChapter
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonObject
import mihon.domain.source.model.LinkedSourceManga
import mihon.domain.source.model.acceptsScanlator
import mihon.domain.source.model.chapterOrigins
import mihon.domain.source.model.linkedSources
import mihon.domain.source.model.withLinkedSources
import org.junit.jupiter.api.Test

class LinkedSourceScanlatorsTest {
    private fun chapter(number: Float, scanlator: String?, url: String) = SChapter.create().also {
        it.name = "Chapter $number"
        it.chapter_number = number
        it.scanlator = scanlator
        it.url = url
    }

    @Test
    fun `summary counts releases per group and preserves decimal range endpoints`() {
        val summaries = LinkedSourceScanlators.summaries(
            "Title",
            listOf(
                chapter(1.1f, "Group A", "/1"),
                chapter(7.5f, "group a", "/2"),
                chapter(3f, "Group B", "/3"),
            ),
        )
        summaries["Group A"] shouldBe LinkedSourceScanlators.Summary(2, 1.1, 7.5)
        summaries["Group B"] shouldBe LinkedSourceScanlators.Summary(1, 3.0, 3.0)
    }

    @Test
    fun `summary recognizes chapter labels when the source has not assigned chapter numbers`() {
        val chapters = listOf(
            chapter(-1f, "Group A", "/1").also { it.name = "Chapter 5" },
            chapter(-1f, "Group A", "/2").also { it.name = "Chapter 12" },
        )
        LinkedSourceScanlators.summaries("Title", chapters)["Group A"] shouldBe
            LinkedSourceScanlators.Summary(2, 5.0, 12.0)
    }

    @Test
    fun `unnumbered releases still have a useful count without inventing a range`() {
        val chapters = listOf(
            chapter(-1f, null, "/a").also { it.name = "Bonus artwork" },
            chapter(-1f, "", "/b").also { it.name = "Author notes" },
        )
        LinkedSourceScanlators.summaries("Title", chapters)[""] shouldBe
            LinkedSourceScanlators.Summary(2, null, null)
    }

    @Test
    fun `scanlator choices deduplicate casing and whitespace and include unnamed releases`() {
        LinkedSourceScanlators.available(
            listOf(
                chapter(1f, "Group A", "/a"),
                chapter(2f, " group a ", "/b"),
                chapter(3f, "Group B", "/c"),
                chapter(4f, null, "/d"),
                chapter(5f, " ", "/e"),
            ),
        ) shouldBe setOf("", "Group A", "Group B")
    }

    @Test
    fun `one scanlator across many chapters needs no multiple-scanlator picker`() {
        LinkedSourceScanlators.available(
            listOf(chapter(1f, "Group A", "/1"), chapter(2f, "GROUP A", "/2")),
        ).size shouldBe 1
    }

    @Test
    fun `filtering before merge chooses the selected release and leaves missing chapters to other sources`() {
        val link = LinkedSourceManga(1, "/manga", "Title", scanlators = setOf("Group A"))
        val source = listOf(
            chapter(1f, "Group B", "/wrong-1"),
            chapter(1f, "group a", "/chosen-1"),
            chapter(2f, "Group B", "/wrong-2"),
        ).filter { link.acceptsScanlator(it.scanlator) }
        val merged = MultiSourceChapterMerger.merge(
            1,
            listOf(1L to source, 2L to listOf(chapter(1f, "Other", "/other-1"), chapter(2f, "Other", "/other-2"))),
            emptyList(),
            emptySet(),
        ).asReversed()
        merged.first().memo.chapterOrigins().first { it.sourceId == 1L }.url shouldBe "/chosen-1"
        merged.last().memo.chapterOrigins().map { it.sourceId } shouldBe listOf(2L)
    }

    @Test
    fun `selecting unnamed releases does not include named groups`() {
        val link = LinkedSourceManga(1, "/manga", "Title", scanlators = setOf(""))
        link.acceptsScanlator(null) shouldBe true
        link.acceptsScanlator(" ") shouldBe true
        link.acceptsScanlator("Group A") shouldBe false
    }

    @Test
    fun `multiple selected scanlators are included while new unselected groups remain excluded`() {
        val link = LinkedSourceManga(1, "/manga", "Title", scanlators = setOf("Group A", "Group B"))
        link.acceptsScanlator(" Group A ") shouldBe true
        link.acceptsScanlator("GROUP B") shouldBe true
        link.acceptsScanlator("Group C") shouldBe false
    }

    @Test
    fun `source-specific selections round trip and old links retain their unfiltered behavior`() {
        val selected = LinkedSourceManga(1, "/one", "Title", scanlators = setOf("Group A", ""))
        val legacy = LinkedSourceManga(2, "/two", "Title")
        val restored = JsonObject(emptyMap()).withLinkedSources(listOf(selected, legacy)).linkedSources()
        restored shouldBe listOf(selected, legacy)
        restored[0].acceptsScanlator("Group B") shouldBe false
        restored[1].acceptsScanlator("Group B") shouldBe true
    }
}
