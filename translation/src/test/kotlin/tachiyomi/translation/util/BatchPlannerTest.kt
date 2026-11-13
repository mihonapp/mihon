package tachiyomi.translation.util

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class BatchPlannerTest {
    @Test
    fun `batches chunk by provider limit`() {
        BatchPlanner.batches(count = 5, maxBatch = 2) shouldContainExactly listOf(0..1, 2..3, 4..4)
    }

    @Test
    fun `batches handle empty and single item`() {
        BatchPlanner.batches(count = 0, maxBatch = 5).shouldBeEmpty()
        BatchPlanner.batches(count = 1, maxBatch = 5) shouldContainExactly listOf(0..0)
    }

    @Test
    fun `batches treat a zero limit as one`() {
        BatchPlanner.batches(count = 2, maxBatch = 0) shouldContainExactly listOf(0..0, 1..1)
    }

    @Test
    fun `separator round trip`() {
        val texts = listOf("こんにちは", "さようなら")

        val joined = BatchPlanner.joinSeparated(texts)
        BatchPlanner.splitSeparated(joined, expected = 2) shouldBe texts
    }

    @Test
    fun `separator mismatch returns null`() {
        BatchPlanner.splitSeparated("only one", expected = 2).shouldBeNull()
    }

    @Test
    fun `extracts array from fenced reply`() {
        val reply = """
            ```json
            ["你好", "再见"]
            ```
        """.trimIndent()

        BatchPlanner.extractStringArray(reply).shouldNotBeNull() shouldBe listOf("你好", "再见")
    }

    @Test
    fun `extracts array from prose reply`() {
        val reply = "翻译结果如下：[ \"甲\", \"乙\" ] 希望有帮助"

        BatchPlanner.extractStringArray(reply).shouldNotBeNull() shouldBe listOf("甲", "乙")
    }

    @Test
    fun `extracts text fields from object array`() {
        val reply = """[{"text":"你好"},{"text":"再见"}]"""

        BatchPlanner.extractStringArray(reply).shouldNotBeNull() shouldBe listOf("你好", "再见")
    }

    @Test
    fun `garbage reply returns null`() {
        BatchPlanner.extractStringArray("抱歉，我无法翻译。").shouldBeNull()
        BatchPlanner.extractStringArray(null).shouldBeNull()
    }

    @Test
    fun `user prompt carries count and context`() {
        val prompt = BatchPlanner.buildLlmUserPrompt(
            texts = listOf("おはよう", "げんき？"),
            context = listOf("前の台詞 → 之前那句"),
            target = "zh-CN",
        )

        prompt.contains("目标语言：zh-CN") shouldBe true
        prompt.contains("长度必须等于 2") shouldBe true
        prompt.contains("前の台詞") shouldBe true
        prompt.contains("1. おはよう") shouldBe true
    }
}
