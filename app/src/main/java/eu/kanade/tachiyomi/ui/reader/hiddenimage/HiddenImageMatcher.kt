package eu.kanade.tachiyomi.ui.reader.hiddenimage

import eu.kanade.domain.manga.model.HiddenImage

interface HiddenImageMatcher {
    fun findMatch(hiddenImages: List<HiddenImage>, signature: HiddenImageSignature): HiddenImage?
}

class DefaultHiddenImageMatcher(
    private val similarityDistanceThreshold: Int = 8,
) : HiddenImageMatcher {

    override fun findMatch(hiddenImages: List<HiddenImage>, signature: HiddenImageSignature): HiddenImage? {
        hiddenImages.firstOrNull { entry ->
            entry.normalizedImageUrl != null &&
                signature.normalizedImageUrl != null &&
                entry.normalizedImageUrl == signature.normalizedImageUrl
        }?.let { return it }

        hiddenImages.firstOrNull { entry ->
            entry.imageSha256 != null &&
                signature.imageSha256 != null &&
                entry.imageSha256 == signature.imageSha256
        }?.let { return it }

        val candidateHash = signature.imageDhash?.toULongOrNull(16) ?: return null
        return hiddenImages.firstOrNull { entry ->
            val entryHash = entry.imageDhash?.toULongOrNull(16) ?: return@firstOrNull false
            hammingDistance(candidateHash, entryHash) <= similarityDistanceThreshold
        }
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
