package eu.kanade.tachiyomi.ui.reader

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
class ReaderErrorMessageTest {

    @Test
    fun `replaces HTTP source null pointer failures`() {
        val original = NullPointerException()
        val result = original.toReadableReaderError(
            isHttpSource = true,
            sourceExtensionMessage = "Update the extension",
        )

        result.message shouldBe "Update the extension"
        (result.cause === original) shouldBe true
    }

    @Test
    fun `detects wrapped null pointer failures`() {
        val original = IllegalStateException(
            "NullPointerException: null",
            NullPointerException(),
        )
        val result = original.toReadableReaderError(
            isHttpSource = true,
            sourceExtensionMessage = "Update the extension",
        )

        result.message shouldBe "Update the extension"
        (result.cause === original) shouldBe true
    }

    @Test
    fun `preserves informative HTTP source failures`() {
        val original = IllegalStateException("Cloudflare challenge failed")
        val result = original.toReadableReaderError(
            isHttpSource = true,
            sourceExtensionMessage = "Update the extension",
        )

        (result === original) shouldBe true
    }

    @Test
    fun `preserves local source failures`() {
        val original = NullPointerException()
        val result = original.toReadableReaderError(
            isHttpSource = false,
            sourceExtensionMessage = "Update the extension",
        )

        (result === original) shouldBe true
    }
}
