package eu.kanade.tachiyomi.ui.reader.viewer.panel

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import eu.kanade.tachiyomi.data.reader.PanelCacheRepository
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okio.Buffer
import java.security.MessageDigest
import kotlin.math.max

class PanelDetector(
    private val panelCacheRepository: PanelCacheRepository,
    private val subStopGenerator: PanelSubStopGenerator,
) {

    suspend fun detect(page: ReaderPage, imageBytes: Buffer, direction: PanelDirection): List<Panel> {
        val chapterId = page.chapter.chapter.id ?: return listOf(Panel(PanelRect.FULL_PAGE))
        val hash = imageBytes.contentHash()

        withContext(Dispatchers.IO) {
            panelCacheRepository.get(chapterId, page.index, hash, DETECTOR_VERSION)
        }?.let { return it.panels }

        val timedOutOrPanels = withTimeoutOrNull(DETECTION_BUDGET_MS) {
            withContext(Dispatchers.Default) { runDetection(imageBytes, direction) }
        }
        val panels = timedOutOrPanels ?: listOf(Panel(PanelRect.FULL_PAGE))

        // Only persist a genuine detection outcome. A timeout is transient (system load,
        // not a property of the image), so don't pin the page to "no panels" permanently —
        // low-confidence/decode-failure fallbacks inside runDetection ARE deterministic
        // given the same bytes+version and are fine to cache; they'll re-run automatically
        // on a future DETECTOR_VERSION bump.
        if (timedOutOrPanels != null) {
            withContext(Dispatchers.IO) {
                panelCacheRepository.save(chapterId, page.index, hash, DETECTOR_VERSION, PanelPageData(panels))
            }
        }
        return panels
    }

    private suspend fun runDetection(imageBytes: Buffer, direction: PanelDirection): List<Panel> {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(imageBytes.copy().inputStream(), null, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return listOf(Panel(PanelRect.FULL_PAGE))

        val sample = sampleSizeFor(bounds.outWidth, bounds.outHeight, MAX_DETECTION_DIMENSION)
        val smallBitmap = BitmapFactory.decodeStream(
            imageBytes.copy().inputStream(),
            null,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return listOf(Panel(PanelRect.FULL_PAGE))

        val rawRects = PanelBoundaryDetector.detect(smallBitmap.toPixelBuffer(MAX_DETECTION_DIMENSION))
        smallBitmap.recycle()

        if (PanelConfidence.isLowConfidence(rawRects)) return listOf(Panel(PanelRect.FULL_PAGE))

        val ordered = PanelReadingOrder.sort(rawRects, direction)
        val fullBitmap = lazy { BitmapFactory.decodeStream(imageBytes.copy().inputStream()) }
        try {
            return ordered.map { rect ->
                val subStops = subStopGenerator.generate(rect, direction) {
                    fullBitmap.value?.let { cropNormalized(it, rect) }
                }
                Panel(rect, subStops)
            }
        } finally {
            if (fullBitmap.isInitialized()) {
                fullBitmap.value?.recycle()
            }
        }
    }

    private fun cropNormalized(bitmap: Bitmap, rect: PanelRect): Bitmap {
        val left = (rect.left * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
        val top = (rect.top * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
        val right = (rect.right * bitmap.width).toInt().coerceIn(left + 1, bitmap.width)
        val bottom = (rect.bottom * bitmap.height).toInt().coerceIn(top + 1, bitmap.height)
        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }

    private fun sampleSizeFor(width: Int, height: Int, maxDimension: Int): Int {
        var sample = 1
        while (max(width, height) / sample > maxDimension) sample *= 2
        return sample
    }

    private fun Buffer.contentHash(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(snapshot().toByteArray())
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val DETECTION_BUDGET_MS = 2000L
        private const val MAX_DETECTION_DIMENSION = 900
        private const val DETECTOR_VERSION = 1
    }
}
