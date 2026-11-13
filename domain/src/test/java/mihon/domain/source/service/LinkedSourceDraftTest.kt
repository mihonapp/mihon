package mihon.domain.source.service

import io.kotest.matchers.shouldBe
import mihon.domain.source.model.LinkedSourceManga
import org.junit.jupiter.api.Test

class LinkedSourceDraftTest {
    private val original = listOf(LinkedSourceManga(1, "/a", "Title"), LinkedSourceManga(2, "/b", "Title"))

    @Test
    fun `editing and clearing selections leave persisted input untouched until apply`() {
        val draft = LinkedSourceDraft()
        draft.initialize(original)
        draft.remove(1)
        draft.select(LinkedSourceManga(3, "/c", "Title", scanlators = setOf("Group A")))
        original.map { it.sourceId } shouldBe listOf(1L, 2L)
        draft.state.value.links!!.map { it.sourceId } shouldBe listOf(2L, 3L)
        draft.state.value.applied shouldBe false
        draft.clear()
        draft.beginApply() shouldBe emptyList()
        original.size shouldBe 2
    }

    @Test
    fun `opening another results page must not reset the shared editing session`() {
        val draft = LinkedSourceDraft()
        draft.initialize(original)
        draft.remove(1)
        draft.initialize(original)
        draft.state.value.links shouldBe listOf(original[1])
    }

    @Test
    fun `apply freezes its exact source and scanlator choices and cannot be submitted twice`() {
        val draft = LinkedSourceDraft()
        draft.initialize(original)
        val replacement = LinkedSourceManga(2, "/replacement", "Title", scanlators = setOf("Group A", ""))
        draft.select(replacement)
        val snapshot = draft.beginApply()
        draft.clear()
        draft.remove(1)
        draft.select(LinkedSourceManga(3, "/c", "Title"))
        draft.beginApply() shouldBe null
        draft.state.value.links shouldBe snapshot
        snapshot shouldBe listOf(original[0], replacement)
        draft.finishApply(success = true)
        draft.state.value.applied shouldBe true
    }

    @Test
    fun `failed save preserves pending choices and allows another apply`() {
        val draft = LinkedSourceDraft()
        draft.initialize(original)
        draft.remove(1)
        val first = draft.beginApply()
        draft.finishApply(success = false)
        draft.state.value.applied shouldBe false
        draft.beginApply() shouldBe first
    }
}
