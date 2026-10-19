package eu.kanade.tachiyomi.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorSpace
import androidx.core.graphics.createBitmap
import java.io.IOException
import java.io.InputStream
import ca.mpreg.imagedecoder.ImageDecoder as NativeImageDecoder

/**
 * Decodes images with the app's own image decoder, for formats Android can't decode on every
 * version (AVIF, JPEG XL, HEIF).
 */
@Suppress("UNUSED")
object ImageDecoder {

    /**
     * Decode an image, or the first frame of an animated one. Blocking; safe to call from
     * several threads.
     *
     * @param stream the encoded image, read to the end. The caller closes it.
     * @return a new sRGB [Bitmap.Config.ARGB_8888] bitmap, recycled by the caller.
     * @throws IOException if the image can't be decoded.
     */
    fun decode(stream: InputStream): Bitmap = try {
        NativeImageDecoder.open(stream).use { decoder ->
            decoder.decodeNext().use { frame -> frame.toBitmap(decoder.isHdr) }
        }
    } catch (e: IOException) {
        throw e
    } catch (e: Exception) {
        throw IOException(e)
    }

    private fun NativeImageDecoder.Frame.toBitmap(hdr: Boolean): Bitmap {
        val decoded = createBitmap(width, height, if (hdr) Bitmap.Config.RGBA_F16 else Bitmap.Config.ARGB_8888)
        try {
            decoded.copyPixelsFromBuffer(image)
        } catch (e: Exception) {
            decoded.recycle()
            throw e
        }
        if (!hdr) return decoded
        // Sources expect 8-bit sRGB pixels; HDR values above SDR white are clipped
        return try {
            createBitmap(width, height, Bitmap.Config.ARGB_8888, colorSpace = ColorSpace.get(ColorSpace.Named.SRGB))
                .also { Canvas(it).drawBitmap(decoded, 0f, 0f, null) }
        } finally {
            decoded.recycle()
        }
    }
}
