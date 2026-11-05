package eu.kanade.tachiyomi.data.translation.overlay

/** Picks the biggest font size that still fits. Pure, so it can be tested without Android. */
object FontFit {

    /**
     * Binary search for the largest size in [[minSize], [maxSize]] where [fits] is true.
     * [fits] must be monotonic (if a size fits, every smaller size fits too).
     * Returns [minSize] when even the smallest size does not fit.
     */
    fun largestFitting(minSize: Float, maxSize: Float, precision: Float = 0.5f, fits: (Float) -> Boolean): Float {
        if (maxSize <= minSize) return minSize
        if (fits(maxSize)) return maxSize
        if (!fits(minSize)) return minSize

        var low = minSize // fits
        var high = maxSize // does not fit
        while (high - low > precision) {
            val mid = (low + high) / 2f
            if (fits(mid)) low = mid else high = mid
        }
        return low
    }
}
