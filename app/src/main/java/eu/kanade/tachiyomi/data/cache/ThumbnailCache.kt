package eu.kanade.tachiyomi.data.cache

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.util.storage.DiskUtil
import tachiyomi.domain.chapter.model.Chapter
import java.io.File

/**
 * Class used to create thumbnail cache.
 * It is used to store the thumbnails of the chapters of library entries.
 * Names of files are created with the md5 of the thumbnail URL.
 *
 * @param context the application context.
 * @constructor creates an instance of the thumbnail cache.
 */
@Inject
@SingleIn(AppScope::class)
class ThumbnailCache(
    private val context: Context,
) {

    companion object {
        private const val THUMBNAILS_DIR = "thumbnails"
    }

    /**
     * Cache directory used for cache management.
     */
    private val cacheDir by lazy { getCacheDir(THUMBNAILS_DIR) }

    /**
     * Returns the thumbnail from cache.
     *
     * @param chapterThumbnailUrl thumbnail url for the chapter.
     * @return cover image.
     */
    fun getCoverFile(chapterThumbnailUrl: String?): File? {
        return chapterThumbnailUrl?.let {
            File(cacheDir, DiskUtil.hashKeyForDisk(it))
        }
    }

    /**
     * Delete the cover files of the chapter from the cache.
     *
     * @param chapter the chapter.
     * @return number of files that were deleted.
     */
    fun deleteFromCache(chapter: Chapter): Int {
        var deleted = 0

        getCoverFile(chapter.thumbnailUrl)?.let {
            if (it.exists() && it.delete()) ++deleted
        }

        return deleted
    }

    private fun getCacheDir(dir: String): File {
        return context.getExternalFilesDir(dir)
            ?: File(context.filesDir, dir).also { it.mkdirs() }
    }
}
