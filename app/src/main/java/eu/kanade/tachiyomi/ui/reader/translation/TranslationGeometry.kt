package eu.kanade.tachiyomi.ui.reader.translation

import android.graphics.RectF
import androidx.compose.runtime.mutableStateMapOf

/**
 * Where the page image currently sits on screen, published by the page views.
 *
 * Two access paths exist on purpose:
 * - [frame] reads snapshot state, so the overlay repaints whenever a view publishes a new rect;
 * - [read] asks the live view for its rect right now. Publishing relies on the view noticing its
 *   own layout and pan changes, which SubsamplingScaleImageView does a frame late; reading on
 *   demand keeps the boxes glued to the art even then.
 */
object TranslationGeometry {
    /**
     * [rect] is in window coordinates; [sourceWidth]/[sourceHeight] are the dimensions of the image
     * the view is actually showing, which differ from the raw file when borders are cropped or a
     * wide page is split.
     */
    data class Frame(
        val rect: RectF,
        val sourceWidth: Int,
        val sourceHeight: Int,
        val cropBorders: Boolean = false,
    )

    private val frames = mutableStateMapOf<Int, Frame>()

    /** Live readers, one per visible page view. Main thread only. */
    private val providers = HashMap<Int, () -> Frame?>()

    /**
     * Notified on every published frame. The controller uses it to tell "the view is still
     * relaying out" (panel opening, page turn animation) from "geometry has settled", so the
     * scan starts only when the boxes would land where the art actually is.
     */
    var onFramePublished: (() -> Unit)? = null

    fun update(pageIndex: Int, frame: Frame) {
        frames[pageIndex] = frame.copy(rect = RectF(frame.rect))
        onFramePublished?.invoke()
    }

    fun frame(pageIndex: Int): Frame? = frames[pageIndex]

    /** Current rect straight from the view, falling back to the last published one. */
    fun read(pageIndex: Int): Frame? = providers[pageIndex]?.invoke() ?: frames[pageIndex]

    fun register(pageIndex: Int, provider: () -> Frame?) {
        providers[pageIndex] = provider
    }

    fun unregister(pageIndex: Int, provider: () -> Frame?) {
        if (providers[pageIndex] === provider) {
            providers.remove(pageIndex)
            // The holder went away (recycled or detached): its last published rect is stale by
            // definition. Leaving it in let a recycled pager page's frame win the containing
            // test — every pager page shares the same screen rect — which once republished an
            // early page's boxes over whatever page the reader was showing.
            frames.remove(pageIndex)
        }
    }

    /** Drops page rects; view registrations survive a chapter change. */
    /**
     * Page under a window-space point, preferring [fallback] when none contains it.
     *
     * Webtoon pages are drawn one after another with no gaps worth speaking of, so the page that
     * owns the top edge of the viewport is the one being read. The viewer's own current page lags
     * behind that edge, which is why text right at the top of the next page used to be skipped.
     *
     * Two details matter here:
     * - A view that is still registered (live) beats a leftover frame when several rects contain
     *   the point, as they transiently do during a pager page transition.
     * - The NEAREST frame is a real fallback, not a guess to remove: the webtoon viewport's top
     *   edge regularly falls where no frame contains it (holder recycling gap, chapter
     *   transition), and the nearest page there is the one being read. Removing it once broke
     *   top/bottom drawing and anchor sync on long strips.
     */
    fun pageAtWindow(x: Float, y: Float, fallback: Int): Int {
        var liveBest: Int? = null
        var liveBestArea = 0f
        var staleBest: Int? = null
        var staleBestArea = 0f
        var nearest: Int? = null
        var nearestDistance = Float.MAX_VALUE

        for (index in providers.keys + frames.keys) {
            val rect = read(index)?.rect ?: continue
            if (rect.width() <= 0f || rect.height() <= 0f) continue
            if (x >= rect.left && x <= rect.right && y >= rect.top && y <= rect.bottom) {
                val area = rect.width() * rect.height()
                if (providers.containsKey(index)) {
                    if (area > liveBestArea) {
                        liveBestArea = area
                        liveBest = index
                    }
                } else if (area > staleBestArea) {
                    staleBestArea = area
                    staleBest = index
                }
            } else {
                val dx = if (x < rect.left) rect.left - x else if (x > rect.right) x - rect.right else 0f
                val dy = if (y < rect.top) rect.top - y else if (y > rect.bottom) y - rect.bottom else 0f
                val distance = dx * dx + dy * dy
                if (distance < nearestDistance) {
                    nearestDistance = distance
                    nearest = index
                }
            }
        }
        return liveBest ?: staleBest ?: nearest ?: fallback
    }

    /**
     * Drops frames and views for pages far outside [indices]: pages whose holders were recycled
     * leave their last screen rect behind, and reading it back once drew a recycled page's boxes
     * over whatever page the reader was showing.
     */
    fun retain(indices: IntRange) {
        frames.keys.removeAll { it !in indices }
        providers.keys.removeAll { it !in indices }
    }

    fun clear() {
        frames.clear()
    }
}
