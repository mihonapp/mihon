package eu.kanade.tachiyomi.data.cache

import android.content.Context
import android.text.TextUtils
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.bumptech.glide.request.animation.GlideAnimation
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.signature.StringSignature
import eu.kanade.tachiyomi.util.DiskUtils
import java.io.File
import java.io.IOException

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
    private val CACHE_DIRNAME = "cover_disk_cache"
    private val cacheDir: File = File(context.cacheDir, CACHE_DIRNAME)

    /**
     * Download the cover with Glide and save the file.
     * @param thumbnailUrl url of thumbnail.
     * @param headers      headers included in Glide request.
     * @param imageView    imageView where picture should be displayed.
     */
    @JvmOverloads
    fun save(thumbnailUrl: String, headers: LazyHeaders, imageView: ImageView? = null) {
        // Check if url is empty.
        if (TextUtils.isEmpty(thumbnailUrl))
            return

        // Download the cover with Glide and save the file.
        val url = GlideUrl(thumbnailUrl, headers)
        Glide.with(context)
                .load(url)
                .downloadOnly(object : SimpleTarget<File>() {
                    override fun onResourceReady(resource: File, anim: GlideAnimation<in File>) {
                        try {
                            // Copy the cover from Glide's cache to local cache.
                            copyToLocalCache(thumbnailUrl, resource)

                            // Check if imageView isn't null and show picture in imageView.
                            if (imageView != null) {
                                loadFromCache(imageView, resource)
                            }
                        } catch (e: IOException) {
                            // Do nothing.
                        }
                    }
                })
    }

    /**
     * Copy the cover from Glide's cache to this cache.
     * @param thumbnailUrl url of thumbnail.
     * @param sourceFile   the source file of the cover image.
     * @throws IOException exception returned
     */
    @Throws(IOException::class)
    fun copyToLocalCache(thumbnailUrl: String, sourceFile: File) {
        // Get destination file.
        val destFile = File(cacheDir, DiskUtils.hashKeyForDisk(thumbnailUrl))

        sourceFile.copyTo(destFile, overwrite = true)
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
     * Delete the cover file from the cache.
     * @param thumbnailUrl the thumbnail url.
     * @return status of deletion.
     */
    fun deleteCoverFromCache(thumbnailUrl: String): Boolean {
        // Check if url is empty.
        if (TextUtils.isEmpty(thumbnailUrl))
            return false

        // Remove file.
        val file = File(cacheDir, DiskUtils.hashKeyForDisk(thumbnailUrl))
        return file.exists() && file.delete()
    }

    /**
     * Save or load the image from cache
     * @param imageView    imageView where picture should be displayed.
     * @param thumbnailUrl the thumbnail url.
     * @param headers      headers included in Glide request.
     */
    fun saveOrLoadFromCache(imageView: ImageView, thumbnailUrl: String, headers: LazyHeaders) {
        // If file exist load it otherwise save it.
        val localCover = getCoverFromCache(thumbnailUrl)
        if (localCover.exists()) {
            loadFromCache(imageView, localCover)
        } else {
            save(thumbnailUrl, headers, imageView)
        }
    }

    /**
     * Helper method to load the cover from the cache directory into the specified image view.
     * Glide stores the resized image in its cache to improve performance.
     * @param imageView imageView where picture should be displayed.
     * @param file      file to load. Must exist!.
     */
    private fun loadFromCache(imageView: ImageView, file: File) {
        Glide.with(context)
                .load(file)
                .diskCacheStrategy(DiskCacheStrategy.RESULT)
                .centerCrop()
                .signature(StringSignature(file.lastModified().toString()))
                .into(imageView)
    }

    /**
     * Helper method to load the cover from network into the specified image view.
     * The source image is stored in Glide's cache so that it can be easily copied to this cache
     * if the manga is added to the library.
     * @param imageView    imageView where picture should be displayed.
     * @param thumbnailUrl url of thumbnail.
     * @param headers      headers included in Glide request.
     */
    fun loadFromNetwork(imageView: ImageView, thumbnailUrl: String, headers: LazyHeaders) {
        // Check if url is empty.
        if (TextUtils.isEmpty(thumbnailUrl))
            return

        val url = GlideUrl(thumbnailUrl, headers)
        Glide.with(context)
                .load(url)
                .diskCacheStrategy(DiskCacheStrategy.SOURCE)
                .centerCrop()
                .into(imageView)
    }

}
