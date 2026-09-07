package mihon.desktop.ui.reader

import androidx.compose.ui.graphics.Color
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import mihon.desktop.reader.ReaderBackgroundColor
import mihon.desktop.reader.ReaderColorFilter
import org.junit.jupiter.api.Test

class ReaderColorFilterTest {

    @Test
    fun `none filter returns null compose ColorFilter for zero overhead`() {
        ReaderColorFilter.NONE.toComposeColorFilter().shouldBeNull()
    }

    @Test
    fun `all non-none filters return valid ColorFilter instances`() {
        val nonNone = listOf(
            ReaderColorFilter.INVERT,
            ReaderColorFilter.GRAYSCALE,
            ReaderColorFilter.INVERT_GRAYSCALE,
            ReaderColorFilter.SEPIA,
            ReaderColorFilter.NIGHT,
        )

        nonNone.forEach { filter ->
            filter.toComposeColorFilter().shouldNotBeNull()
        }
    }

    @Test
    fun `reader background colors map to expected compose colors`() {
        ReaderBackgroundColor.DARK_GRAY.toComposeColor() shouldBe Color(0xff101010)
        ReaderBackgroundColor.BLACK.toComposeColor() shouldBe Color(0xff000000)
        ReaderBackgroundColor.WHITE.toComposeColor() shouldBe Color(0xffffffff)
        ReaderBackgroundColor.WARM_CREAM.toComposeColor() shouldBe Color(0xfff5efeb)
    }

    @Test
    fun `invert color matrix has negative diagonals`() {
        val values = InvertMatrix.values
        values.size shouldBe 20
        // R = 255 - R
        values[0] shouldBe -1f
        values[4] shouldBe 255f
        // G = 255 - G
        values[6] shouldBe -1f
        values[9] shouldBe 255f
        // B = 255 - B
        values[12] shouldBe -1f
        values[14] shouldBe 255f
    }
}
