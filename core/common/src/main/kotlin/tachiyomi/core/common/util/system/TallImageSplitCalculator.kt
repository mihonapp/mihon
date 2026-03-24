package tachiyomi.core.common.util.system

internal object TallImageSplitCalculator {

    fun calculatePartCount(imageHeight: Int, optimalImageHeight: Int): Int {
        require(imageHeight > 0) { "imageHeight must be positive" }
        require(optimalImageHeight > 0) { "optimalImageHeight must be positive" }
        // -1 so it doesn't try to split when imageHeight = optimalImageHeight
        return (imageHeight - 1) / optimalImageHeight + 1
    }

    fun shouldSplit(imageWidth: Int, imageHeight: Int, optimalImageHeight: Int): Boolean {
        require(imageWidth > 0) { "imageWidth must be positive" }
        return imageHeight > imageWidth * 3 &&
            calculatePartCount(imageHeight, optimalImageHeight) > 1
    }
}
