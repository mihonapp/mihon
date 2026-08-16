package eu.kanade.tachiyomi.ui.reader.viewer.panel

import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognizer
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TextAwarePanelSubStopGeneratorTest {

    // Text.TextBlock's superclass is package-private with a non-trivial constructor, which
    // defeats MockK's proxy-based mocking; its public constructor is used directly instead.
    //
    // Under this module's stub android.jar (no Robolectric), android.graphics.Rect's own
    // constructors are no-ops that leave every field at 0 -- including the *internal* defensive
    // copy TextBase's constructor makes of the boundingBox we pass in. Field assignment (not
    // constructor calls) still works because it's a direct field write, so the box's fields are
    // set after construction, and the same technique reflectively overwrites TextBlock's private
    // boundingBox field, bypassing the broken internal copy.
    private fun rect(left: Int, top: Int, right: Int, bottom: Int): Rect =
        Rect().apply {
            this.left = left
            this.top = top
            this.right = right
            this.bottom = bottom
        }

    private fun textBlock(box: Rect): Text.TextBlock {
        val block = Text.TextBlock("", rect(0, 0, 0, 0), emptyList<Point>(), "", null, emptyList<Text.Line>())
        val boundingBoxField = Class.forName("com.google.mlkit.vision.text.Text\$TextBase")
            .getDeclaredField("zzb")
            .apply { isAccessible = true }
        boundingBoxField.set(block, box)
        return block
    }

    private fun textResult(blocks: List<Text.TextBlock>): Text = mockk {
        every { textBlocks } returns blocks
    }

    // The real com.google.android.gms.tasks.Tasks.forResult(...) posts listener callbacks
    // through TaskExecutors.MAIN_THREAD, which requires a live android.os.Looper. There's no
    // Robolectric shadow in this module's unit tests, so a Task is mocked to invoke its
    // success listener synchronously instead, matching what an already-completed Task does.
    private fun completedTask(result: Text): Task<Text> {
        val task = mockk<Task<Text>>()
        every { task.addOnSuccessListener(any()) } answers {
            firstArg<OnSuccessListener<Text>>().onSuccess(result)
            task
        }
        every { task.addOnFailureListener(any()) } returns task
        return task
    }

    @Test
    fun `narrow panels never call the recognizer`() = runTest {
        val recognizer = mockk<TextRecognizer>()
        val generator = TextAwarePanelSubStopGenerator(recognizer)
        val panel = PanelRect(0f, 0f, 0.4f, 0.5f)

        val stops = generator.generate(panel, PanelDirection.LTR) { mockk() }

        assertTrue(stops.isEmpty())
    }

    @Test
    fun `wide panel with no detected text falls back to geometric stops`() = runTest {
        val recognizer = mockk<TextRecognizer>()
        every { recognizer.process(any<InputImage>()) } returns completedTask(textResult(emptyList()))
        val generator = TextAwarePanelSubStopGenerator(recognizer)
        val panel = PanelRect(0f, 0f, 0.9f, 0.15f)
        val bitmap = mockk<Bitmap> {
            every { width } returns 900
            every { height } returns 150
        }

        mockkStatic(InputImage::class)
        every { InputImage.fromBitmap(any(), any()) } returns mockk<InputImage>()
        val stops = try {
            generator.generate(panel, PanelDirection.LTR) { bitmap }
        } finally {
            unmockkStatic(InputImage::class)
        }

        val expected = GeometricPanelSubStopGenerator.generate(panel, PanelDirection.LTR) { bitmap }
        assertEquals(expected, stops)
    }

    @Test
    fun `wide panel with two separated text blocks produces two stops plus the full reveal`() = runTest {
        val recognizer = mockk<TextRecognizer>()
        val blocks = listOf(
            textBlock(rect(50, 20, 150, 100)),
            textBlock(rect(700, 20, 800, 100)),
        )
        every { recognizer.process(any<InputImage>()) } returns completedTask(textResult(blocks))
        val generator = TextAwarePanelSubStopGenerator(recognizer)
        val panel = PanelRect(0f, 0f, 0.9f, 0.15f)
        val bitmap = mockk<Bitmap> {
            every { width } returns 900
            every { height } returns 150
        }

        mockkStatic(InputImage::class)
        every { InputImage.fromBitmap(any(), any()) } returns mockk<InputImage>()
        val stops = try {
            generator.generate(panel, PanelDirection.LTR) { bitmap }
        } finally {
            unmockkStatic(InputImage::class)
        }

        assertEquals(3, stops.size)
        assertEquals(panel, stops.last())
        assertTrue(stops[0].left < stops[1].left)
    }
}
