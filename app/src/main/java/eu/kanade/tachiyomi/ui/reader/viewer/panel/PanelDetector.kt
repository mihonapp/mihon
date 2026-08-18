package eu.kanade.tachiyomi.ui.reader.viewer.panel

import android.content.Context
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
    context: Context,
    private val panelCacheRepository: PanelCacheRepository,
) {
    private val mlDetector by lazy { MlPanelBoundaryDetector.tryCreate(context) }

    suspend fun detect(page: ReaderPage, imageBytes: Buffer, direction: PanelDirection): List<Panel> {
        val chapterId = page.chapter.chapter.id ?: return listOf(Panel(PanelRect.FULL_PAGE))
        val hash = imageBytes.contentHash()
        // Reading direction changes both the reading order AND the merge/divide profile the
        // pipeline applies (see PanelPipeline), so it's part of what the cached result depends
        // on — fold it into the version key or toggling RTL/LTR would keep serving whichever
        // direction a page was first detected under.
        val version = cacheVersion(direction)

        withContext(Dispatchers.IO) {
            panelCacheRepository.get(chapterId, page.index, hash, version)
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
                panelCacheRepository.save(chapterId, page.index, hash, version, PanelPageData(panels))
            }
        }
        return panels
    }

    private fun cacheVersion(direction: PanelDirection): Int = DETECTOR_VERSION * 10 + direction.ordinal

    private suspend fun runDetection(imageBytes: Buffer, direction: PanelDirection): List<Panel> {
        val detector = mlDetector ?: return listOf(Panel(PanelRect.FULL_PAGE))

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(imageBytes.copy().inputStream(), null, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return listOf(Panel(PanelRect.FULL_PAGE))

        val sample = sampleSizeFor(bounds.outWidth, bounds.outHeight, MAX_DETECTION_DIMENSION)
        val smallBitmap = BitmapFactory.decodeStream(
            imageBytes.copy().inputStream(),
            null,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return listOf(Panel(PanelRect.FULL_PAGE))

        val rects = detector.detect(smallBitmap, rightToLeft = direction == PanelDirection.RTL)
        smallBitmap.recycle()

        // Sub-stops (multiple zoom stops within one wide panel) aren't used here: the ML pipeline's
        // own merge/divide planner already produces exactly the final stop list, so every entry —
        // whether it's a raw detected panel or a planned merge/split piece — is exactly one stop.
        return rects.map { rect -> Panel(rect) }
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

    fun close() {
        mlDetector?.close()
    }

    companion object {
        private const val DETECTION_BUDGET_MS = 2000L
        private const val MAX_DETECTION_DIMENSION = 900
        private const val DETECTOR_VERSION = 12
    }
}
