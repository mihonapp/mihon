package eu.kanade.tachiyomi.ui.reader.hiddenimage

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import eu.kanade.tachiyomi.util.lang.Hash
import java.io.InputStream
import androidx.core.graphics.scale
import androidx.core.graphics.get

class HiddenImageFingerprintFactory {

    fun create(
        imageUrl: String?,
        streamProvider: (() -> InputStream)?,
    ): HiddenImageSignature {
        val rawImageUrl = imageUrl?.trim()?.takeIf(String::isNotEmpty)
        val normalizedImageUrl = normalizeImageUrl(rawImageUrl)
        if (streamProvider == null) {
            return HiddenImageSignature(
                imageUrl = rawImageUrl,
                normalizedImageUrl = normalizedImageUrl,
                imageSha256 = null,
                imageDhash = null,
            )
        }

        val bytes = runCatching { streamProvider().use { it.readBytes() } }.getOrNull()

        val sha256 = bytes?.let(Hash::sha256)
        val dHash = bytes?.let(::computeDHash)

        return HiddenImageSignature(
            imageUrl = rawImageUrl,
            normalizedImageUrl = normalizedImageUrl,
            imageSha256 = sha256,
            imageDhash = dHash,
        )
    }

    private fun computeDHash(bytes: ByteArray): String? {
        val decodedBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val scaledBitmap = decodedBitmap.scale(9, 8, false)

        var hash = 0uL
        var bitIndex = 0
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val left = scaledBitmap[x, y].toLuma()
                val right = scaledBitmap[x + 1, y].toLuma()
                if (left > right) {
                    hash = hash or (1uL shl bitIndex)
                }
                bitIndex++
            }
        }

        if (scaledBitmap != decodedBitmap) {
            decodedBitmap.recycle()
        }
        scaledBitmap.recycle()

        return hash.toString(16).padStart(16, '0')
    }

    private fun Int.toLuma(): Int {
        val r = this shr 16 and 0xFF
        val g = this shr 8 and 0xFF
        val b = this and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000
    }

    private fun normalizeImageUrl(url: String?): String? {
        val raw = url?.trim()?.takeIf(String::isNotEmpty) ?: return null
        return raw.substringBefore('#').substringBefore('?')
    }
}
