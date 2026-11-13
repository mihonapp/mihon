package eu.kanade.tachiyomi.ui.reader.translation

import android.graphics.BitmapFactory
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import java.io.File

/**
 * Copies the current page's stream to a cache file so OCR can decode arbitrary regions at full
 * resolution. `ReaderPage.stream` is a factory, but reading it repeatedly while the page turns is
 * wasteful, so one copy per page is kept for the lifetime of the translation session.
 */
class TranslationPageCache(private val dir: File) {
    private val sizes = HashMap<Int, Pair<Int, Int>?>()

    fun fileFor(page: ReaderPage): File? {
        val target = File(dir, "page_${page.index}.img")
        if (target.exists() && target.length() > 0) return target
        val stream = page.stream ?: return null
        return runCatching {
            dir.mkdirs()
            stream().use { input -> target.outputStream().use { output -> input.copyTo(output) } }
            target
        }.getOrNull()
    }

    /** Width/height of the page image, read from the header only. */
    fun sizeOf(page: ReaderPage): Pair<Int, Int>? {
        sizes[page.index]?.let { return it }
        val file = fileFor(page) ?: return null
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        val size = if (options.outWidth > 0 && options.outHeight > 0) {
            options.outWidth to options.outHeight
        } else {
            null
        }
        sizes[page.index] = size
        return size
    }

    /** Drops a copy that turned out to be unreadable, so the next attempt re-reads the stream. */
    fun invalidate(page: ReaderPage) {
        sizes.remove(page.index)
        runCatching { File(dir, "page_${page.index}.img").delete() }
    }

    fun clear() {
        sizes.clear()
        runCatching { dir.deleteRecursively() }
    }
}
