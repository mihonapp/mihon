package eu.kanade.tachiyomi.ui.reader.hiddenimage

import eu.kanade.domain.manga.model.HiddenImage

interface HiddenImageMatcher {
    fun findMatch(hiddenImages: Sequence<HiddenImage>, signature: HiddenImageSignature): HiddenImage?
}

class DefaultHiddenImageMatcher(
    private val similarityDistanceThreshold: Int = 8,
) : HiddenImageMatcher {

    override fun findMatch(hiddenImages: Sequence<HiddenImage>, signature: HiddenImageSignature): HiddenImage? {
        val signatureSha256 = signature.imageSha256
        val candidateHash = signature.imageDhash?.toULongOrNull(16)
        var dhashMatch: HiddenImage? = null

        for (entry in hiddenImages) {
            if (
                signatureSha256 != null &&
                entry.imageSha256 != null &&
                entry.imageSha256 == signatureSha256
            ) {
                return entry
            }

            if (dhashMatch == null && candidateHash != null) {
                val entryHash = entry.imageDhash?.toULongOrNull(16) ?: continue
                if (hammingDistance(candidateHash, entryHash) <= similarityDistanceThreshold) {
                    dhashMatch = entry
                }
            }
        }

        return dhashMatch
    }

    private fun hammingDistance(a: ULong, b: ULong): Int {
        var x = a xor b
        var count = 0
        while (x != 0uL) {
            count++
            x = x and (x - 1u)
        }
        return count
    }
}
