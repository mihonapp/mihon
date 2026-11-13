package tachiyomi.translation.model

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class TranslationSessionTest {
    private val rect = NormalizedRect(0.1f, 0.1f, 0.4f, 0.4f)

    private fun spread(index: Int) = SpreadKey(chapterId = 7L, anchorIndex = index)

    private fun page(index: Int) = PageKey(chapterId = 7L, pageIndex = index)

    @Test
    fun `entries are numbered in drawing order`() {
        val session = TranslationSession()

        session.add(spread(0), page(0), rect)
        session.add(spread(0), page(0), rect)
        session.add(spread(0), page(0), rect)

        session.entriesOf(spread(0)).map { it.seq } shouldBe listOf(1, 2, 3)
    }

    @Test
    fun `only the capped number of spreads are kept and the oldest is evicted`() {
        val session = TranslationSession()
        val cap = TranslationSession.MAX_SPREADS

        (0..cap).forEach { session.add(spread(it), page(it), rect) }

        session.activeSpreads() shouldHaveSize cap
        session.hasSpread(spread(0)) shouldBe false
        session.hasSpread(spread(cap)) shouldBe true
    }

    @Test
    fun `re-adding to an existing spread keeps it alive`() {
        val session = TranslationSession()
        val cap = TranslationSession.MAX_SPREADS

        session.add(spread(0), page(0), rect)
        (1 until cap).forEach { session.add(spread(it), page(it), rect) }

        // Touch spread 0 again, then push one more so the eviction target is spread 1.
        session.add(spread(0), page(0), rect)
        session.add(spread(cap), page(cap), rect)

        session.hasSpread(spread(0)) shouldBe true
        session.hasSpread(spread(1)) shouldBe false
    }

    @Test
    fun `removing a middle entry renumbers the rest`() {
        val session = TranslationSession()
        val first = session.add(spread(0), page(0), rect)
        val second = session.add(spread(0), page(0), rect)
        val third = session.add(spread(0), page(0), rect)

        session.remove(second.id) shouldBe true

        val remaining = session.entriesOf(spread(0))
        remaining.map { it.id } shouldBe listOf(first.id, third.id)
        remaining.map { it.seq } shouldBe listOf(1, 2)
    }

    @Test
    fun `removing the last entry drops the spread`() {
        val session = TranslationSession()
        val entry = session.add(spread(0), page(0), rect)

        session.remove(entry.id) shouldBe true

        session.hasSpread(spread(0)) shouldBe false
        session.activeSpreads() shouldHaveSize 0
    }

    @Test
    fun `update replaces the entry in place`() {
        val session = TranslationSession()
        val entry = session.add(spread(0), page(0), rect)

        session.update(entry.id) { it.copy(state = EntryState.READY, translation = "你好") }

        session.find(entry.id).shouldNotBeNull().translation shouldBe "你好"
        session.entriesOf(spread(0)).single().state shouldBe EntryState.READY
    }

    @Test
    fun `snapshot round trips through json`() {
        val session = TranslationSession()
        val entry = session.add(spread(2), page(3), rect)
        session.update(entry.id) { it.copy(state = EntryState.READY, sourceText = "こんにちは", translation = "你好") }

        val restored = TranslationSession().apply {
            restore(TranslationSession.fromJson(TranslationSession.toJson(session.snapshot()))!!)
        }

        restored.entriesOf(spread(2)).single().translation shouldBe "你好"
        restored.entriesOf(spread(2)).single().sourceText shouldBe "こんにちは"
    }

    @Test
    fun `restore keeps at most the capped number of spreads`() {
        val session = TranslationSession()
        val cap = TranslationSession.MAX_SPREADS
        (0..cap + 1).forEach { session.add(spread(it), page(it), rect) }

        val restored = TranslationSession().apply { restore(session.snapshot()) }

        restored.activeSpreads() shouldHaveSize cap
        restored.hasSpread(spread(0)) shouldBe false
    }

    @Test
    fun `ids stay unique after removals`() {
        val session = TranslationSession()
        val first = session.add(spread(0), page(0), rect)
        session.remove(first.id)
        val second = session.add(spread(0), page(0), rect)

        (second.id != first.id) shouldBe true
        second.seq shouldBe 1
    }
}
