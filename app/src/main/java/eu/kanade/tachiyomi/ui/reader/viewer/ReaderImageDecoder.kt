package eu.kanade.tachiyomi.ui.reader.viewer

internal fun shouldUseSubsamplingDecoder(
    isWebtoon: Boolean,
    alwaysDecodeLongStripWithSSIV: Boolean,
    canUseHardwareBitmap: Boolean,
): Boolean {
    return !isWebtoon || alwaysDecodeLongStripWithSSIV || !canUseHardwareBitmap
}
