package eu.kanade.tachiyomi.ui.reader.hiddenimage

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Bitmap.CompressFormat
import eu.kanade.tachiyomi.util.lang.Hash
import java.io.ByteArrayOutputStream
import java.io.InputStream
import androidx.core.graphics.scale
import androidx.core.graphics.get

class HiddenImageFingerprintFactory {

    fun create(streamProvider: (() -> InputStream)?): HiddenImageSignature {
        if (streamProvider == null) {
            return HiddenImageSignature(
                imageSha256 = null,
                imageDhash = null,
                previewImage = null,
            )
        }

        val bytes = runCatching { streamProvider().use { it.readBytes() } }.getOrNull()

        val sha256 = bytes?.let(Hash::sha256)
        val dHash = bytes?.let(::computeDHash)
        val previewImage = bytes?.let(::computePreviewImage)

        return HiddenImageSignature(
            imageSha256 = sha256,
            imageDhash = dHash,
            previewImage = previewImage,
        )
    }

    private fun computePreviewImage(bytes: ByteArray): ByteArray? {
        val decodedBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val targetWidth = 360
        val targetHeight = 520
        val scale = minOf(
            targetWidth.toFloat() / decodedBitmap.width.toFloat(),
            targetHeight.toFloat() / decodedBitmap.height.toFloat(),
            1f,
        )

        val scaledBitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                decodedBitmap,
                (decodedBitmap.width * scale).toInt().coerceAtLeast(1),
                (decodedBitmap.height * scale).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            decodedBitmap
        }

        val output = ByteArrayOutputStream()
        scaledBitmap.compress(CompressFormat.JPEG, 82, output)

        if (scaledBitmap != decodedBitmap) {
            decodedBitmap.recycle()
        }
        scaledBitmap.recycle()

        return output.toByteArray()
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

}
