package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.annotation.SuppressLint
import android.content.Context
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.LinearLayout
import eu.kanade.tachiyomi.ui.reader.model.PageSpread
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.widget.ViewPagerAdapter

/**
 * View of the ViewPager that shows two pages of a chapter side by side as a spread.
 *
 * [reuse] lets the adapter hand back an already-decoded [PagerPageHolder] for one of this spread's
 * pages (a shift/reload re-pairs the same pages), so it's re-parented here instead of being torn
 * down and re-decoded. A reused half keeps its bitmap; it's held hidden until this spread's joint
 * scale is applied and then revealed with its sibling.
 */
@SuppressLint("ViewConstructor")
class PagerSpreadHolder(
    readerThemedContext: Context,
    private val viewer: PagerViewer,
    private val spread: PageSpread,
    reuse: ((ReaderPage) -> PagerPageHolder?)? = null,
) : LinearLayout(readerThemedContext), ViewPagerAdapter.PositionableView {

    override val item: Any
        get() = spread

    val leftHolder: PagerPageHolder
    val rightHolder: PagerPageHolder

    private val scaleCoordinator: SpreadScaleCoordinator

    // A reused half is already decoded, so it never re-fires onImageLoaded. It reports ready only at
    // first layout, so its new joint scale is computed against real slot widths, not the pre-layout 0.
    private var pendingReadyLeft = false
    private var pendingReadyRight = false

    init {
        orientation = HORIZONTAL
        layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)

        val (screenLeft, screenRight) = PagerNavigation.screenOrder(spread, viewer is L2RPagerViewer)

        val reusedLeft = reuse?.invoke(screenLeft)
        leftHolder = reusedLeft ?: PagerPageHolder(readerThemedContext, viewer, screenLeft, spread)
        reusedLeft?.hideImageForReuse()

        val reusedRight = reuse?.invoke(screenRight)
        rightHolder = reusedRight ?: PagerPageHolder(readerThemedContext, viewer, screenRight, spread)
        reusedRight?.hideImageForReuse()

        addHalf(leftHolder)
        addHalf(rightHolder)

        scaleCoordinator = SpreadScaleCoordinator(viewer, leftHolder, rightHolder)
        wire(leftHolder)
        wire(rightHolder)

        pendingReadyLeft = reusedLeft != null
        pendingReadyRight = reusedRight != null
    }

    /** Teardown hook: lets the coordinator drop its pending reveal timer before the halves are reused. */
    fun detach() {
        scaleCoordinator.detach()
    }

    /**
     * The page under [rawScreenX] (a raw-screen x-coordinate), for a per-page action on the pressed
     * half. Splits at the real seam between the two slots (off-centre when the halves have unequal
     * widths), not the viewport midpoint.
     */
    fun pageAt(rawScreenX: Float): ReaderPage {
        val location = IntArray(2)
        rightHolder.getLocationOnScreen(location)
        return (if (rawScreenX < location[0]) leftHolder else rightHolder).page
    }

    private fun addHalf(holder: PagerPageHolder) {
        (holder.parent as? ViewGroup)?.removeView(holder) // detach from a previous spread if reused
        addView(holder, LayoutParams(0, MATCH_PARENT, 1f))
    }

    private fun wire(holder: PagerPageHolder) {
        holder.onImageLoaded = { scaleCoordinator.onHolderReady(holder) }
        // A failed half can't contribute to the joint scale; reveal the pair rather than leaving the
        // good half held back INVISIBLE.
        holder.onImageLoadError = { scaleCoordinator.onHolderError(holder) }
        holder.onScaleChanged = { scaleCoordinator.onStateChanged(holder) }
        holder.onCenterChanged = { scaleCoordinator.onStateChanged(holder) }
    }

    // The combined-canvas joint scale is a one-off computed value (unlike every other scale type,
    // which gets resize-resilience for free from the image view's own per-draw minScale()), so it
    // has to be (re)derived on layout: computed on first layout, re-fit on a genuine resize
    // (multi-window/freeform). oldw/oldh are 0 on the very first layout.
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        val firstLayout = oldw == 0 || oldh == 0
        // This container's onSizeChanged runs *before* its children are laid out, so the half holders
        // don't have their real slot widths yet; computing the joint scale now would pair against a
        // stale/zero slot. Defer to after the layout pass, when both children have their true
        // half-widths, and let the coordinator (re)compute against valid geometry.
        post {
            // Reused (already-decoded) halves never re-fire onImageLoaded, so they report ready here.
            if (pendingReadyLeft) {
                pendingReadyLeft = false
                scaleCoordinator.onHolderReady(leftHolder)
            }
            if (pendingReadyRight) {
                pendingReadyRight = false
                scaleCoordinator.onHolderReady(rightHolder)
            }
            // First layout completes the initial joint scale (for halves that decoded before layout);
            // a later size change re-fits it.
            if (firstLayout) scaleCoordinator.onSpreadLaidOut() else scaleCoordinator.onViewportResized()
        }
    }
}
