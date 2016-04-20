package eu.kanade.tachiyomi.data.cache

import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.bumptech.glide.request.animation.GlideAnimation
import com.bumptech.glide.request.target.SimpleTarget
import eu.kanade.tachiyomi.util.DiskUtils
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * Class used to create cover cache.
 * It is used to store the covers of the library.
 * Makes use of Glide (which can avoid repeating requests) to download covers.
 * Names of files are created with the md5 of the thumbnail URL.
 *
 * @param context the application context.
 * @constructor creates an instance of the cover cache.
 */
class CoverCache(private val context: Context) {

    /**
     * Cache directory used for cache management.
     */
    private val cacheDir: File = File(context.externalCacheDir, "cover_disk_cache")

    /**
     * Download the cover with Glide and save the file.
     * @param thumbnailUrl url of thumbnail.
     * @param headers      headers included in Glide request.
     * @param onReady      function to call when the image is ready
     */
    fun save(thumbnailUrl: String?, headers: LazyHeaders?, onReady: ((File) -> Unit)? = null) {
        // Check if url is empty.
        if (thumbnailUrl.isNullOrEmpty())
            return

        // Download the cover with Glide and save the file.
        val url = GlideUrl(thumbnailUrl, headers)
        Glide.with(context)
                .load(url)
                .downloadOnly(object : SimpleTarget<File>() {
                    override fun onResourceReady(resource: File, anim: GlideAnimation<in File>) {
                        try {
                            // Copy the cover from Glide's cache to local cache.
                            copyToCache(thumbnailUrl!!, resource)

                            onReady?.invoke(resource)
                        } catch (e: IOException) {
                            // Do nothing.
                        }
                    }
                })
    }

    /**
     * Save or load the image from cache
     * @param thumbnailUrl the thumbnail url.
     * @param headers      headers included in Glide request.
     * @param onReady      function to call when the image is ready
     */
    fun saveOrLoadFromCache(thumbnailUrl: String?, headers: LazyHeaders?, onReady: ((File) -> Unit)?) {
        // Check if url is empty.
        if (thumbnailUrl.isNullOrEmpty())
            return

        // If file exist load it otherwise save it.
        val localCover = getCoverFromCache(thumbnailUrl!!)
        if (localCover.exists()) {
            onReady?.invoke(localCover)
        } else {
            save(thumbnailUrl, headers, onReady)
        }
    }

    /**
     * Returns the cover from cache.
     * @param thumbnailUrl the thumbnail url.
     * @return cover image.
     */
    private fun getCoverFromCache(thumbnailUrl: String): File {
        return File(cacheDir, DiskUtils.hashKeyForDisk(thumbnailUrl))
    }

    /**
     * Copy the given file to this cache.
     * @param thumbnailUrl url of thumbnail.
     * @param sourceFile   the source file of the cover image.
     * @throws IOException if there's any error.
     */
    @Throws(IOException::class)
    fun copyToCache(thumbnailUrl: String, sourceFile: File) {
        // Get destination file.
        val destFile = getCoverFromCache(thumbnailUrl)

        sourceFile.copyTo(destFile, overwrite = true)
    }

    /**
     * Copy the given stream to this cache.
     * @param thumbnailUrl url of the thumbnail.
     * @param inputStream  the stream to copy.
     * @throws IOException if there's any error.
     */
    @Throws(IOException::class)
    fun copyToCache(thumbnailUrl: String, inputStream: InputStream) {
        // Get destination file.
        val destFile = getCoverFromCache(thumbnailUrl)

        destFile.outputStream().use { inputStream.copyTo(it) }
    }

    /**
     * Delete the cover file from the cache.
     * @param thumbnailUrl the thumbnail url.
     * @return status of deletion.
     */
    fun deleteFromCache(thumbnailUrl: String?): Boolean {
        // Check if url is empty.
        if (thumbnailUrl.isNullOrEmpty())
            return false

        // Remove file.
        val file = File(cacheDir, DiskUtils.hashKeyForDisk(thumbnailUrl))
        return file.exists() && file.delete()
    }

}
