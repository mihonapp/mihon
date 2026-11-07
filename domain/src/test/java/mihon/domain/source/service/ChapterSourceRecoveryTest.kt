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

class ChapterSourceRecoveryTest {
    private fun chapter(): Chapter = Chapter.create().copy(
        id = 42,
        mangaId = 7,
        url = "/local-storage-name",
        bookmark = true,
        memo = JsonObject(emptyMap()).withChapterOrigins(
            (1L..3L).map { sourceId ->
                ChapterOrigin.from(
                    sourceId,
                    SChapter.create().also {
                        it.url = "/source-$sourceId/chapter-1"
                        it.name = "Chapter 1"
                        it.chapter_number = 1f
                    },
                )
            },
        ),
    )

    @Test
    fun `outage advances through alternatives without loops or losing saved origins`() {
        val original = chapter()
        val second = ChapterSourceRecovery.recover(original, setOf(1, 2, 3), setOf(1))!!
        second.sourceId(1) shouldBe 2L
        second.assignedSourceId() shouldBe 2L
        second.id shouldBe original.id
        second.url shouldBe original.url
        second.bookmark shouldBe true
        second.origins().toSet() shouldBe original.origins().toSet()
        val third = ChapterSourceRecovery.recover(second, setOf(1, 2, 3), setOf(1, 2))!!
        third.sourceId(1) shouldBe 3L
        ChapterSourceRecovery.recover(third, setOf(1, 2, 3), setOf(1, 2, 3)) shouldBe null
        // A later manual retry still has all confirmed providers available.
        ChapterSourceRecovery.recover(third, setOf(1), emptySet())!!.sourceId(1) shouldBe 1L
    }

    @Test
    fun `missing extension recovers to an installed alternative`() {
        ChapterSourceRecovery.recover(chapter(), setOf(3), emptySet())!!.sourceId(1) shouldBe 3L
        ChapterSourceRecovery.recover(chapter(), emptySet(), emptySet()) shouldBe null
    }

    @Test
    fun `partially read chapter never switches versions`() {
        ChapterSourceRecovery.recover(chapter().copy(lastPageRead = 4), setOf(2, 3), setOf(1)) shouldBe null
    }

    @Test
    fun `fallback prefers healthy providers but still makes a bounded attempt if all are cooling down`() {
        ChapterSourceRecovery.recover(chapter(), setOf(2, 3), setOf(1), setOf(2))!!.sourceId(1) shouldBe 3L
        ChapterSourceRecovery.recover(chapter(), setOf(2, 3), setOf(1), setOf(2, 3))!!.sourceId(1) shouldBe 2L
    }

    @Test
    fun `outage cooldown expires automatically and successful provider clears it early`() {
        var now = 100L
        val state = SourceRecoveryState(cooldownMillis = 1_000, nowMillis = { now })
        state.failed(1)
        now = 1_099
        state.unavailableSources() shouldBe setOf(1L)
        now = 1_100
        state.unavailableSources() shouldBe emptySet()
        state.failed(1)
        state.succeeded(1)
        state.unavailableSources() shouldBe emptySet()
    }

    @Test
    fun `download ahead batch avoids a failing source even when its queue is shorter`() {
        val planned = ChapterDownloadPlanner.plan(
            listOf(chapter(), chapter().copy(id = 43)),
            1,
            setOf(1, 2, 3),
            mapOf(2L to 5, 3L to 5),
            unavailableSourceIds = setOf(1),
        )
        planned.map { it.sourceId(1) } shouldBe listOf(2L, 3L)
    }

    @Test
    fun `cooldown does not silently drop chapters with only one installed provider`() {
        val planned = ChapterDownloadPlanner.plan(
            listOf(chapter()),
            1,
            setOf(1),
            emptyMap(),
            unavailableSourceIds = setOf(1),
        )
        planned.single().sourceId(1) shouldBe 1L
    }
}
