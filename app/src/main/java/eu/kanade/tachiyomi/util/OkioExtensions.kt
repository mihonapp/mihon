package eu.kanade.tachiyomi.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import okio.BufferedSource
import okio.Okio
import java.io.File
import java.io.OutputStream

/**
 * Saves the given source to a file and closes it. Directories will be created if needed.
 *
 * @param file the file where the source is copied.
 */
fun BufferedSource.saveTo(file: File) {
    try {
        // Create parent dirs if needed
        file.parentFile.mkdirs()

        // Copy to destination
        saveTo(file.outputStream())
    } catch (e: Exception) {
        close()
        file.delete()
        throw e
    }
}

/**
 * Saves the given source to an output stream and closes both resources.
 *
 * @param stream the stream where the source is copied.
 */
fun BufferedSource.saveTo(stream: OutputStream) {
    use { input ->
        Okio.buffer(Okio.sink(stream)).use {
            it.writeAll(input)
            it.flush()
        }
    }
}

/**
 * Saves the given source to an output stream and closes both resources.
 * The source is expected to be an image, and it may reencode the image.
 *
 * @param stream the stream where the source is copied.
 * @param reencode whether to reencode the image or not.
 */
fun BufferedSource.saveImageTo(stream: OutputStream, reencode: Boolean = false) {
    if (reencode) {
        use {
            val bitmap = BitmapFactory.decodeStream(it.inputStream())
            stream.use {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
            }
            bitmap.recycle()
        }
    } else {
        saveTo(stream)
    }
}