package mihon.domain.source.service

import eu.kanade.tachiyomi.source.model.SChapter
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonObject
import mihon.domain.source.model.ChapterOrigin
import mihon.domain.source.model.assignedSourceId
import mihon.domain.source.model.origins
import mihon.domain.source.model.sourceId
import mihon.domain.source.model.withChapterOrigins
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter

class ChapterDownloadPlannerTest {
    private fun chapter(id: Long): Chapter {
        val raw = SChapter.create().also {
            it.url = "/$id"
            it.name = "Chapter $id"
        }
        return Chapter.create().copy(
            id = id,
            memo = JsonObject(emptyMap()).withChapterOrigins(
                listOf(ChapterOrigin.from(2, raw), ChapterOrigin.from(1, raw)),
            ),
        )
    }

    @Test
    fun `arbitrary subset is balanced independently of chapter number`() {
        val planned = ChapterDownloadPlanner.plan(listOf(chapter(2), chapter(4)), 1, setOf(1, 2), emptyMap())
        planned.map { it.sourceId(1) } shouldBe listOf(1L, 2L)
        planned.map { it.id } shouldBe listOf(2L, 4L)
        planned.map { it.assignedSourceId() } shouldBe listOf(1L, 2L)
    }

    @Test
    fun `existing queue load is included in assignment`() {
        val planned = ChapterDownloadPlanner.plan(listOf(chapter(1), chapter(2)), 1, setOf(1, 2), mapOf(1L to 3))
        planned.map { it.sourceId(1) } shouldBe listOf(2L, 2L)
    }

    @Test
    fun `partially read chapter keeps its current version`() {
        val chapter = chapter(1).copy(lastPageRead = 5)
        val planned = ChapterDownloadPlanner.plan(listOf(chapter), 1, setOf(1, 2), mapOf(2L to 9)).single()
        planned.lastPageRead shouldBe chapter.lastPageRead
        planned.origins() shouldBe chapter.origins()
        planned.assignedSourceId() shouldBe 2L
    }

    @Test
    fun `uninstalled source is skipped and alternatives are retained`() {
        val planned = ChapterDownloadPlanner.plan(listOf(chapter(1)), 1, setOf(1), emptyMap()).single()
        planned.sourceId(1) shouldBe 1L
        planned.origins().size shouldBe 2
    }

    @Test
    fun `single source chapter keeps its metadata`() {
        val chapter = Chapter.create().copy(id = 1)
        ChapterDownloadPlanner.plan(listOf(chapter), 1, setOf(1), emptyMap()).single() shouldBe chapter
    }
}
