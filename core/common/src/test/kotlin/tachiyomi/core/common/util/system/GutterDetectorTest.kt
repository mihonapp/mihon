package tachiyomi.core.common.util.system

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class GutterDetectorTest {

    private val width = 198
    private val height = 297

    private val centerColumns = 93..104
    private val leftEdgeColumns = 0 until 9
    private val rightEdgeColumns = 188 until 198

    /** A synthetic page as `[x][y]` light flags. */
    private fun page(
        width: Int = this.width,
        height: Int = this.height,
        isLight: (x: Int, y: Int) -> Boolean,
    ): Array<BooleanArray> = Array(width) { x -> BooleanArray(height) { y -> isLight(x, y) } }

    private fun detect(page: Array<BooleanArray>) =
        hasVerticalGutter(page.size, page[0].size) { x, y -> page[x][y] }

    @Test
    fun `detects a light gutter down the middle of a two-page spread`() {
        val page = page { x, _ -> x in centerColumns }

        detect(page) shouldBe true
    }

    @Test
    fun `tolerates a single dark row in the middle of the gutter`() {
        val page = page { x, _ -> x in centerColumns }
        page[98][height / 2] = false

        detect(page) shouldBe true
    }

    @Test
    fun `rejects a gutter with too many dark rows`() {
        val page = page { x, _ -> x in centerColumns }
        for (y in 140..149) page[98][y] = false

        detect(page) shouldBe false
    }

    @Test
    fun `rejects a full-bleed cover with no light gutter`() {
        val page = page { _, _ -> false }

        detect(page) shouldBe false
    }

    @Test
    fun `rejects a page without dark pixels at the left edge`() {
        val page = page { x, _ -> x !in rightEdgeColumns }

        detect(page) shouldBe false
    }

    @Test
    fun `rejects a page without dark pixels at the right edge`() {
        val page = page { x, _ -> x !in leftEdgeColumns }

        detect(page) shouldBe false
    }

    @Test
    fun `rejects bands smaller than the minimum size`() {
        detect(page(width = 7) { x, _ -> x == 3 }) shouldBe false
        detect(page(height = 7) { x, _ -> x in centerColumns }) shouldBe false
    }

    @Test
    fun `rejects a page whose gutter covers only half the height`() {
        val page = page { x, y -> x in centerColumns && y < height / 2 }

        detect(page) shouldBe false
    }

    @Test
    fun `keeps a gutter with dark rows just under the limit`() {
        val page = page { x, _ -> x in centerColumns }
        for (y in 140..144) page[98][y] = false

        detect(page) shouldBe true
    }

    @Test
    fun `rejects a gutter with dark rows just over the limit`() {
        val page = page { x, _ -> x in centerColumns }
        for (y in 140..145) page[98][y] = false

        detect(page) shouldBe false
    }

    @Test
    fun `accepts dark pixels at the outermost edge columns`() {
        val page = page { x, _ -> x in centerColumns || x == 0 || x == width - 1 }

        detect(page) shouldBe true
    }

    @Test
    fun `rejects dark pixels just outside the edge ranges`() {
        // The edge bands are light; the nearest dark columns sit one past each band
        val page = page { x, _ -> x != 9 && x != 187 }

        detect(page) shouldBe false
    }
}
