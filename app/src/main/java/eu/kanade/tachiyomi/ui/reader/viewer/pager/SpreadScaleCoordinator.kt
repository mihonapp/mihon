package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.graphics.PointF
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.LinearLayout
import androidx.core.view.doOnLayout
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import kotlin.math.abs

/**
 * Treats a spread's two [PagerPageHolder]s as one logical page for scale-type purposes. Each
 * holder starts out independently auto-fit to the user's real scale-type preference (see
 * [PagerPageHolder.setImage]); once both have reported their native size via [onHolderReady],
 * this computes a scale/center for each against their *combined* shape and overrides the
 * independent auto-fit with it, so the pair fills its space edge-to-edge without resorting to a
 * scale type the user didn't actually choose.
 *
 * The geometry itself lives in the pure [SpreadScaleCalculator] (unit-tested); this class only
 * reads the two holders' live sizes, feeds them in, and applies the result back to the views.
 *
 * If one half never reports (a load error, or it's simply much slower than the other), the
 * other is left at its own independent auto-fit rather than the pair waiting indefinitely.
 */
class SpreadScaleCoordinator(
    private val viewer: PagerViewer,
    private val leftHolder: PagerPageHolder,
    private val rightHolder: PagerPageHolder,
) {

    private var leftReady = false
    private var rightReady = false

    // Set once applyJointScale() succeeds; live pan/zoom sync is meaningless before then (no
    // fixed scale ratio or reference center yet to sync against).
    private var initialCenterXLeft = 0f
    private var initialCenterXRight = 0f
    private var initialCenterYLeft = 0f
    private var initialCenterYRight = 0f
    private var scaleRatioRightOverLeft = 1f
    private var jointScaleApplied = false

    // Reentrancy guard for onStateChanged's sync: the library fires the state-changed listener only from
    // touch and animation, never from a direct setSpreadScaleAndCenter(), so a sync can't currently recurse
    // into itself. A cheap safety net in case that stops holding.
    private var syncing = false

    // Both halves are held INVISIBLE until the joint scale is known, then revealed together, so the
    // pair never paints at its per-half solo fit first and then snaps to the joint fit (a visible
    // reflow). See PagerPageHolder.deferInitialReveal / ReaderPageImageView.deferReveal.
    private val handler = Handler(Looper.getMainLooper())
    private val revealRunnable = Runnable { revealHalves() }
    private var revealScheduled = false
    private var revealed = false

    fun onHolderReady(holder: PagerPageHolder) {
        when {
            holder === leftHolder -> leftReady = true
            holder === rightHolder -> rightReady = true
            else -> return
        }
        // Safety net: reveal even if the second half never reports and never errors (a stuck decode).
        // Long enough that a normal, even heavy, second decode wins the race so no solo fit is shown.
        if (!revealScheduled && !revealed) {
            revealScheduled = true
            handler.postDelayed(revealRunnable, REVEAL_FALLBACK_MS)
        }
        // Reveal only once the joint scale has been applied (both halves ready and the spread laid out,
        // so slot widths are known; see the reused-holder path in PagerSpreadHolder).
        if (leftReady && rightReady && !jointScaleApplied && applyJointScale()) {
            revealHalves()
        }
    }

    /**
     * The spread has now been laid out with real slot widths. The joint scale needs both halves
     * decoded and the spread laid out, and those finish in either order. A fresh half often decodes
     * first, so [onHolderReady]'s own attempt bailed on a zero slot. This is the layout-side attempt,
     * so whichever of {decode, layout} completes last still triggers a valid computation. A no-op if a
     * half isn't decoded yet (onHolderReady will complete it) or the joint scale is already applied.
     */
    fun onSpreadLaidOut() {
        if (leftReady && rightReady && !jointScaleApplied && applyJointScale()) {
            revealHalves()
        }
    }

    /**
     * A half failed to decode, so no joint scale can be computed; reveal whatever's ready (the good
     * half at its own fit; the failed half shows its error UI) rather than staying hidden.
     */
    fun onHolderError(holder: PagerPageHolder) {
        revealHalves()
    }

    private fun revealHalves() {
        if (revealed) return
        revealed = true
        handler.removeCallbacks(revealRunnable)
        leftHolder.revealDeferredImage()
        rightHolder.revealDeferredImage()
    }

    // Called when the spread is torn down: drop any pending reveal-fallback timer so it can't fire on a
    // half already salvaged into a new pairing (which would paint it at the old scale for a frame before
    // that pairing's own coordinator applies the joint scale).
    fun detach() {
        revealed = true
        handler.removeCallbacks(revealRunnable)
    }

    /**
     * Re-derives the joint scale against the spread's new viewport size, e.g. the app's window
     * resizing in multi-window/freeform mode. Unlike the initial computation, this preserves
     * whichever source point was already centered on screen rather than resetting to plain
     * center, so a resize doesn't throw away where the reader was looking.
     */
    fun onViewportResized() {
        if (jointScaleApplied) applyJointScale(preservePosition = true)
    }

    /**
     * Mirrors [source]'s current live scale/pan onto its sibling, keeping the pair's initial
     * scale ratio and moving both by the same screen-pixel amount on both axes, so
     * panning/zooming either half moves the other in lockstep and the pair can never visually
     * drift apart. A half with no real pan room of its own clamps back to center regardless
     * of what's requested here (see SubsamplingScaleImageView's own PAN_LIMIT_INSIDE), so mirroring
     * is harmless for it. The math is [SpreadScaleCalculator.syncTarget].
     */
    fun onStateChanged(source: PagerPageHolder) {
        if (!jointScaleApplied || syncing) return
        val target = when (source) {
            leftHolder -> rightHolder
            rightHolder -> leftHolder
            else -> return
        }
        val sourceCenter = source.currentCenter ?: return
        val sync = SpreadScaleCalculator.syncTarget(
            sourceScale = source.currentScale,
            sourceCenterX = sourceCenter.x,
            sourceCenterY = sourceCenter.y,
            sourceIsLeft = source === leftHolder,
            scaleRatioRightOverLeft = scaleRatioRightOverLeft,
            initialCenterXLeft = initialCenterXLeft,
            initialCenterYLeft = initialCenterYLeft,
            initialCenterXRight = initialCenterXRight,
            initialCenterYRight = initialCenterYRight,
        ) ?: return

        syncing = true
        try {
            target.setSpreadScaleAndCenter(sync.scale, PointF(sync.centerX, sync.centerY))
        } finally {
            syncing = false
        }
    }

    /** Returns true if the joint scale was computed and applied (false if the halves aren't
     *  decoded or the spread isn't laid out yet, so the caller shouldn't reveal). */
    private fun applyJointScale(preservePosition: Boolean = false): Boolean {
        val nativeWidthLeft = leftHolder.sWidth
        val nativeHeightLeft = leftHolder.sHeight
        val nativeWidthRight = rightHolder.sWidth
        val nativeHeightRight = rightHolder.sHeight
        if (nativeWidthLeft <= 0 || nativeHeightLeft <= 0 || nativeWidthRight <= 0 ||
            nativeHeightRight <= 0
        ) {
            return false
        }

        // Size the slots to the halves' logical widths so the seam lands at the pair's natural split
        // (equal widths keep 1:1). Changing them re-lays out the children, so fit against the new slots
        // on the next layout, not this one.
        if (ensureProportionalSlots(nativeWidthLeft, nativeHeightLeft, nativeWidthRight, nativeHeightRight)) {
            leftHolder.doOnLayout { onSpreadLaidOut() }
            return false
        }

        val slotWidthLeft = leftHolder.width
        val slotWidthRight = rightHolder.width
        val viewportHeight = leftHolder.height
        // Each slot must be laid out, not just their sum: a half can finish decoding before the spread
        // (or its sibling) is laid out and call this with one slot still zero. Pairing against a zero
        // slot computes (and then latches) a placement that abuts one page short of the seam (the
        // asymmetric centre gap), which nothing recomputes until the holder is torn down.
        if (slotWidthLeft <= 0 || slotWidthRight <= 0 || viewportHeight <= 0) return false

        val preserveLeft = if (preservePosition) leftHolder.currentCenter else null
        val preserveRight = if (preservePosition) rightHolder.currentCenter else null

        val placement = SpreadScaleCalculator.computePlacement(
            nativeWidthLeft = nativeWidthLeft,
            nativeHeightLeft = nativeHeightLeft,
            nativeWidthRight = nativeWidthRight,
            nativeHeightRight = nativeHeightRight,
            slotWidthLeft = slotWidthLeft,
            slotWidthRight = slotWidthRight,
            viewportHeight = viewportHeight,
            scaleType = scaleTypeOf(viewer.config.imageScaleType),
            verticalFit = viewer.config.spreadVerticalFit,
            preserveCenterLeftX = preserveLeft?.x,
            preserveCenterLeftY = preserveLeft?.y,
            preserveCenterRightX = preserveRight?.x,
            preserveCenterRightY = preserveRight?.y,
        )

        initialCenterXLeft = placement.initialCenterXLeft
        initialCenterYLeft = placement.initialCenterYLeft
        initialCenterXRight = placement.initialCenterXRight
        initialCenterYRight = placement.initialCenterYRight
        scaleRatioRightOverLeft = placement.scaleRatioRightOverLeft
        jointScaleApplied = true

        applyHalf(leftHolder, placement.left)
        applyHalf(rightHolder, placement.right)
        return true
    }

    private fun applyHalf(holder: PagerPageHolder, half: SpreadScaleCalculator.Half) {
        holder.setSpreadScaleAndCenter(half.scale, PointF(half.centerX, half.centerY))
        holder.setSpreadPadding(
            left = half.padding.left,
            top = half.padding.top,
            right = half.padding.right,
            bottom = half.padding.bottom,
        )
    }

    /**
     * Sets each half's `LinearLayout` weight to its logical-width share, so the pair's seam sits at the
     * natural split (see [SpreadScaleCalculator.slotWeights]). Returns true when the weights change; the
     * caller then defers fitting to the next layout. An equal-width pair keeps the default 1:1 (returns
     * false, no re-layout).
     */
    private fun ensureProportionalSlots(
        nativeWidthLeft: Int,
        nativeHeightLeft: Int,
        nativeWidthRight: Int,
        nativeHeightRight: Int,
    ): Boolean {
        val (weightLeft, weightRight) = SpreadScaleCalculator.slotWeights(
            nativeWidthLeft,
            nativeHeightLeft,
            nativeWidthRight,
            nativeHeightRight,
            viewer.config.spreadVerticalFit,
        )
        val currentLeft = (leftHolder.layoutParams as? LinearLayout.LayoutParams)?.weight ?: return false
        val currentRight = (rightHolder.layoutParams as? LinearLayout.LayoutParams)?.weight ?: return false
        if (abs(currentLeft - weightLeft) < WEIGHT_EPSILON && abs(currentRight - weightRight) < WEIGHT_EPSILON) {
            return false
        }
        leftHolder.layoutParams = LinearLayout.LayoutParams(0, MATCH_PARENT, weightLeft)
        rightHolder.layoutParams = LinearLayout.LayoutParams(0, MATCH_PARENT, weightRight)
        return true
    }

    companion object {
        /** Slot weights within this of the current ones count as unchanged, avoiding a needless re-layout
         * from float noise (weights are normalised to sum 2, so this is an absolute per-side tolerance). */
        private const val WEIGHT_EPSILON = 0.001f

        /** Safety-net timeout for revealing a spread whose second half never reports ready and never
         * errors. Comfortably longer than a normal heavy decode so it doesn't pre-empt a clean joint
         * reveal, but bounded so a stuck half can't leave the pair blank indefinitely. */
        private const val REVEAL_FALLBACK_MS = 3000L

        /** Maps the library's int scale-type constant to the calculator's own enum. Shared with the
         * justified-lone-page path in [PagerPageHolder]. */
        fun scaleTypeOf(imageScaleType: Int): SpreadScaleCalculator.ScaleType = when (imageScaleType) {
            SubsamplingScaleImageView.SCALE_TYPE_CENTER_CROP -> SpreadScaleCalculator.ScaleType.STRETCH
            SubsamplingScaleImageView.SCALE_TYPE_FIT_WIDTH -> SpreadScaleCalculator.ScaleType.FIT_WIDTH
            SubsamplingScaleImageView.SCALE_TYPE_FIT_HEIGHT -> SpreadScaleCalculator.ScaleType.FIT_HEIGHT
            SubsamplingScaleImageView.SCALE_TYPE_ORIGINAL_SIZE -> SpreadScaleCalculator.ScaleType.ORIGINAL
            SubsamplingScaleImageView.SCALE_TYPE_SMART_FIT -> SpreadScaleCalculator.ScaleType.SMART_FIT
            // SCALE_TYPE_CENTER_INSIDE, and any unrecognized value.
            else -> SpreadScaleCalculator.ScaleType.FIT_SCREEN
        }
    }
}
