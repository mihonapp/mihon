package eu.kanade.tachiyomi.ui.reader.viewer.pager

import eu.kanade.tachiyomi.ui.reader.setting.SpreadVerticalFit
import kotlin.math.roundToInt

/**
 * Pure geometry for a spread, extracted from [SpreadScaleCoordinator] so it can be
 * unit-tested without a running view (it depends on no Android types). Given both halves' native
 * pixel sizes, the spread's per-half slots and viewport height, the chosen scale type, and the two
 * fine-tune flags, it produces the scale, source-pixel centre, and edge padding to apply to each
 * half so the pair renders as one logical page. It also returns the reference values the live
 * pan/zoom sync needs, and provides that sync's math ([syncTarget]).
 *
 * The unequal-height fit ([SpreadVerticalFit]) is a single axis: `MATCH` upsamples the shorter half
 * to the taller's height (so nothing is left to align), while `TOP`/`CENTER` keep native sizes and
 * position the shorter half against the taller.
 *
 * Scale types are this class's own [ScaleType], not `SubsamplingScaleImageView`'s int constants;
 * the (Android) coordinator maps at the boundary, keeping this file dependency-free.
 */
object SpreadScaleCalculator {

    /** Fit modes, named to match the reader's own labels. `FIT_SCREEN`/`STRETCH` are the library's
     * `CENTER_INSIDE`/`CENTER_CROP`; anything unrecognised maps to `FIT_SCREEN` at the boundary. */
    enum class ScaleType { FIT_SCREEN, STRETCH, FIT_WIDTH, FIT_HEIGHT, ORIGINAL, SMART_FIT }

    data class Padding(val left: Int, val top: Int, val right: Int, val bottom: Int)

    data class Half(val scale: Float, val centerX: Float, val centerY: Float, val padding: Padding)

    data class Placement(
        val left: Half,
        val right: Half,
        // Reference values captured at placement time, used later by [syncTarget].
        val scaleRatioRightOverLeft: Float,
        val initialCenterXLeft: Float,
        val initialCenterYLeft: Float,
        val initialCenterXRight: Float,
        val initialCenterYRight: Float,
    )

    /**
     * Scale + padding for a facing pair: the joint placement of both halves. [preserveCenter*] carry
     * the currently-centred source point of each half (from a live resize) so the reader doesn't jump;
     * pass null to reset to plain centre. Requires positive native dimensions; the caller
     * ([SpreadScaleCoordinator]) guards non-positive dims, since unguarded a zero native dimension
     * divides to a NaN/Infinity scale.
     */
    @Suppress("LongParameterList")
    fun computePlacement(
        nativeWidthLeft: Int,
        nativeHeightLeft: Int,
        nativeWidthRight: Int,
        nativeHeightRight: Int,
        slotWidthLeft: Int,
        slotWidthRight: Int,
        viewportHeight: Int,
        scaleType: ScaleType,
        verticalFit: SpreadVerticalFit,
        preserveCenterLeftX: Float? = null,
        preserveCenterLeftY: Float? = null,
        preserveCenterRightX: Float? = null,
        preserveCenterRightY: Float? = null,
    ): Placement {
        // Resolution normalization: upsample the lower-resolution half to the higher one's height,
        // never downsample the higher one, so no detail already present is lost.
        val scaleToMatch = verticalFit == SpreadVerticalFit.MATCH
        val logicalWidthLeft: Float
        val logicalHeightLeft: Float
        val logicalWidthRight: Float
        val logicalHeightRight: Float
        if (scaleToMatch) {
            val targetHeight = maxOf(nativeHeightLeft, nativeHeightRight).toFloat()
            logicalHeightLeft = targetHeight
            logicalWidthLeft = nativeWidthLeft * (targetHeight / nativeHeightLeft)
            logicalHeightRight = targetHeight
            logicalWidthRight = nativeWidthRight * (targetHeight / nativeHeightRight)
        } else {
            logicalWidthLeft = nativeWidthLeft.toFloat()
            logicalHeightLeft = nativeHeightLeft.toFloat()
            logicalWidthRight = nativeWidthRight.toFloat()
            logicalHeightRight = nativeHeightRight.toFloat()
        }

        val combinedWidth = logicalWidthLeft + logicalWidthRight
        val combinedHeight = maxOf(logicalHeightLeft, logicalHeightRight)
        val viewportWidth = (slotWidthLeft + slotWidthRight).toFloat()
        val viewportHeightF = viewportHeight.toFloat()

        val overall = overallScale(scaleType, combinedWidth, combinedHeight, viewportWidth, viewportHeightF)

        val scaleLeft = logicalWidthLeft / nativeWidthLeft * overall
        val scaleRight = logicalWidthRight / nativeWidthRight * overall

        val leftRenderedWidth = nativeWidthLeft * scaleLeft
        val rightRenderedWidth = nativeWidthRight * scaleRight
        val leftRenderedHeight = logicalHeightLeft * overall
        val rightRenderedHeight = logicalHeightRight * overall

        // Unequal-height alignment: TOP biases a shorter half toward its taller sibling's own top
        // edge; CENTER needs no padding (the view's own default centering already lines up). MATCH
        // has equal heights so neither applies. Horizontal is always biased to the seam (all slack to
        // the outer edge) so the two halves stay abutting.
        val alignTop =
            verticalFit == SpreadVerticalFit.TOP && leftRenderedHeight != rightRenderedHeight
        val combinedRenderedHeight = maxOf(leftRenderedHeight, rightRenderedHeight)
        val boxTop = (viewportHeightF - combinedRenderedHeight) / 2f

        fun verticalPadding(renderedHeight: Float): Pair<Int, Int> {
            if (!alignTop || renderedHeight >= combinedRenderedHeight) return 0 to 0
            val slack = (viewportHeightF - renderedHeight).roundToInt()
            // The shorter half can still overflow the viewport (e.g. two tall pages under fit-width),
            // leaving no slack to distribute, so fall back to plain centring rather than aligning.
            if (slack <= 0) return 0 to 0
            val top = boxTop.roundToInt().coerceIn(0, slack)
            return top to (slack - top).coerceAtLeast(0)
        }

        val (topLeft, bottomLeft) = verticalPadding(leftRenderedHeight)
        val (topRight, bottomRight) = verticalPadding(rightRenderedHeight)
        val slackLeft = (slotWidthLeft - leftRenderedWidth).roundToInt().coerceAtLeast(0)
        val slackRight = (slotWidthRight - rightRenderedWidth).roundToInt().coerceAtLeast(0)

        return Placement(
            left = Half(
                scale = scaleLeft,
                centerX = preserveCenterLeftX ?: (nativeWidthLeft / 2f),
                centerY = preserveCenterLeftY ?: (nativeHeightLeft / 2f),
                // Left holder's inner (seam) edge is its right, so slack goes to its left.
                padding = Padding(left = slackLeft, top = topLeft, right = 0, bottom = bottomLeft),
            ),
            right = Half(
                scale = scaleRight,
                centerX = preserveCenterRightX ?: (nativeWidthRight / 2f),
                centerY = preserveCenterRightY ?: (nativeHeightRight / 2f),
                padding = Padding(left = 0, top = topRight, right = slackRight, bottom = bottomRight),
            ),
            scaleRatioRightOverLeft = scaleRight / scaleLeft,
            initialCenterXLeft = nativeWidthLeft / 2f,
            initialCenterYLeft = nativeHeightLeft / 2f,
            initialCenterXRight = nativeWidthRight / 2f,
            initialCenterYRight = nativeHeightRight / 2f,
        )
    }

    /**
     * Placement for a *justified* lone page: fit it into one half-width slot at [scaleType], then
     * bias it to the seam (viewport centre) on the given side so it sits where its would-be
     * partner's inner edge is, leaving the outer half blank, the recto/verso of a physical book.
     * The lone page's holder is the full viewport width, so the far half is padded off entirely and
     * the near half's leftover slack pushes the page to the seam. Wide pages don't use this (they
     * span both page-widths); the caller skips them. Requires a positive native width/height (the caller guards).
     */
    fun soloPlacement(
        nativeWidth: Int,
        nativeHeight: Int,
        viewportWidth: Int,
        viewportHeight: Int,
        scaleType: ScaleType,
        alignLeft: Boolean,
    ): Half {
        val slotWidth = viewportWidth / 2f
        val overall =
            overallScale(scaleType, nativeWidth.toFloat(), nativeHeight.toFloat(), slotWidth, viewportHeight.toFloat())
        val renderedWidth = nativeWidth * overall
        val slack = (slotWidth - renderedWidth).roundToInt().coerceAtLeast(0)
        val farHalf = (viewportWidth - slotWidth).roundToInt()
        val padding = if (alignLeft) {
            // Page in the left half, pushed right to the seam: block the right half, slack on the left.
            Padding(left = slack, top = 0, right = farHalf, bottom = 0)
        } else {
            Padding(left = farHalf, top = 0, right = slack, bottom = 0)
        }
        return Half(scale = overall, centerX = nativeWidth / 2f, centerY = nativeHeight / 2f, padding = padding)
    }

    /** Fit scale of the combined canvas against the viewport, mirroring the library's own per-type
     * `minScale()` formulas applied to the combined shape rather than a single image. Requires positive
     * dimensions (the caller guards). */
    fun overallScale(
        scaleType: ScaleType,
        combinedWidth: Float,
        combinedHeight: Float,
        viewportWidth: Float,
        viewportHeight: Float,
    ): Float = when (scaleType) {
        ScaleType.STRETCH -> maxOf(viewportWidth / combinedWidth, viewportHeight / combinedHeight)
        ScaleType.FIT_WIDTH -> viewportWidth / combinedWidth
        ScaleType.FIT_HEIGHT -> viewportHeight / combinedHeight
        ScaleType.ORIGINAL -> 1f
        ScaleType.SMART_FIT ->
            if (combinedHeight > combinedWidth) viewportWidth / combinedWidth else viewportHeight / combinedHeight
        ScaleType.FIT_SCREEN -> minOf(viewportWidth / combinedWidth, viewportHeight / combinedHeight)
    }

    /**
     * The two half-slots' relative widths, proportional to each half's logical (post-[verticalFit])
     * width (the widths [computePlacement] fits), so the seam sits at the pair's natural split and
     * neither half overflows its slot. Normalised to sum 2 (an equal-width pair is the default 1:1).
     * Apply as the halves' `LinearLayout` weights before [computePlacement] reads the slot widths.
     */
    fun slotWeights(
        nativeWidthLeft: Int,
        nativeHeightLeft: Int,
        nativeWidthRight: Int,
        nativeHeightRight: Int,
        verticalFit: SpreadVerticalFit,
    ): Pair<Float, Float> {
        if (nativeWidthLeft <= 0 || nativeHeightLeft <= 0 || nativeWidthRight <= 0 || nativeHeightRight <= 0) {
            return 1f to 1f
        }
        val logicalWidthLeft: Float
        val logicalWidthRight: Float
        if (verticalFit == SpreadVerticalFit.MATCH) {
            val targetHeight = maxOf(nativeHeightLeft, nativeHeightRight).toFloat()
            logicalWidthLeft = nativeWidthLeft * (targetHeight / nativeHeightLeft)
            logicalWidthRight = nativeWidthRight * (targetHeight / nativeHeightRight)
        } else {
            logicalWidthLeft = nativeWidthLeft.toFloat()
            logicalWidthRight = nativeWidthRight.toFloat()
        }
        val sum = logicalWidthLeft + logicalWidthRight
        if (sum <= 0f) return 1f to 1f
        return (2f * logicalWidthLeft / sum) to (2f * logicalWidthRight / sum)
    }

    data class Sync(val scale: Float, val centerX: Float, val centerY: Float)

    /**
     * Mirrors a source half's live scale/pan onto its sibling: the target keeps the pair's fixed
     * scale ratio and moves by the same screen-pixel delta on both axes, so the pair never drifts
     * apart. Returns null if the scales are degenerate.
     */
    @Suppress("LongParameterList")
    fun syncTarget(
        sourceScale: Float,
        sourceCenterX: Float,
        sourceCenterY: Float,
        sourceIsLeft: Boolean,
        scaleRatioRightOverLeft: Float,
        initialCenterXLeft: Float,
        initialCenterYLeft: Float,
        initialCenterXRight: Float,
        initialCenterYRight: Float,
    ): Sync? {
        if (sourceScale <= 0f) return null
        val targetScale =
            if (sourceIsLeft) sourceScale * scaleRatioRightOverLeft else sourceScale / scaleRatioRightOverLeft
        if (targetScale <= 0f) return null

        val initialCenterXSource = if (sourceIsLeft) initialCenterXLeft else initialCenterXRight
        val initialCenterXTarget = if (sourceIsLeft) initialCenterXRight else initialCenterXLeft
        val initialCenterYSource = if (sourceIsLeft) initialCenterYLeft else initialCenterYRight
        val initialCenterYTarget = if (sourceIsLeft) initialCenterYRight else initialCenterYLeft

        val deltaScreenX = (sourceCenterX - initialCenterXSource) * sourceScale
        val deltaScreenY = (sourceCenterY - initialCenterYSource) * sourceScale
        return Sync(
            scale = targetScale,
            centerX = initialCenterXTarget + deltaScreenX / targetScale,
            centerY = initialCenterYTarget + deltaScreenY / targetScale,
        )
    }
}
