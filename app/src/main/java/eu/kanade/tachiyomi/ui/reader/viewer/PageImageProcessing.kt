package eu.kanade.tachiyomi.ui.reader.viewer

import eu.kanade.tachiyomi.ui.reader.model.InsertPage
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import okio.BufferedSource
import tachiyomi.core.common.util.system.ImageUtil

/**
 * The image of [page] as a pager shows it: a wide page rotated to fit or split in two, the half on the reading
 * side first, so [isLeftToRight] tells which. [onPageSplit] is told of the [InsertPage] for the second half.
 */
fun DualPageSettings.processPagedImage(
    page: ReaderPage,
    imageSource: BufferedSource,
    isLeftToRight: Boolean,
    onPageSplit: (InsertPage) -> Unit,
): BufferedSource {
    if (dualPageRotateToFit) {
        return rotateDualPage(imageSource)
    }

    if (!dualPageSplit) {
        return imageSource
    }

    if (page is InsertPage) {
        return splitInHalf(imageSource, page, isLeftToRight)
    }

    val isDoublePage = ImageUtil.isWideImage(imageSource)
    if (!isDoublePage) {
        return imageSource
    }

    onPageSplit(InsertPage(page))

    return splitInHalf(imageSource, page, isLeftToRight)
}

/**
 * The image of a page as a strip shows it: a wide page rotated to fit, or split with its halves one above the other.
 */
fun DualPageSettings.processStripImage(imageSource: BufferedSource): BufferedSource {
    if (dualPageRotateToFit) {
        return rotateDualPage(imageSource)
    }

    if (dualPageSplit) {
        val isDoublePage = ImageUtil.isWideImage(imageSource)
        if (isDoublePage) {
            val upperSide = if (dualPageInvert) ImageUtil.Side.LEFT else ImageUtil.Side.RIGHT
            return ImageUtil.splitAndMerge(imageSource, upperSide)
        }
    }

    return imageSource
}

private fun DualPageSettings.rotateDualPage(imageSource: BufferedSource): BufferedSource {
    val isDoublePage = ImageUtil.isWideImage(imageSource)
    return if (isDoublePage) {
        val rotation = if (dualPageRotateToFitInvert) -90f else 90f
        ImageUtil.rotateImage(imageSource, rotation)
    } else {
        imageSource
    }
}

private fun DualPageSettings.splitInHalf(
    imageSource: BufferedSource,
    page: ReaderPage,
    isLeftToRight: Boolean,
): BufferedSource {
    var side = when {
        isLeftToRight && page is InsertPage -> ImageUtil.Side.RIGHT
        !isLeftToRight && page is InsertPage -> ImageUtil.Side.LEFT
        isLeftToRight && page !is InsertPage -> ImageUtil.Side.LEFT
        else -> ImageUtil.Side.RIGHT
    }

    if (dualPageInvert) {
        side = when (side) {
            ImageUtil.Side.RIGHT -> ImageUtil.Side.LEFT
            ImageUtil.Side.LEFT -> ImageUtil.Side.RIGHT
        }
    }

    return ImageUtil.splitInHalf(imageSource, side)
}
