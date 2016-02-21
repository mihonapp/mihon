package eu.kanade.tachiyomi.data.cache

import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory
import com.bumptech.glide.module.GlideModule

/**
 * Class used to update Glide module settings
 */
class CoverGlideModule : GlideModule {

    override fun applyOptions(context: Context, builder: GlideBuilder) {
        // Set the cache size of Glide to 15 MiB
        builder.setDiskCache(InternalCacheDiskCacheFactory(context, 15 * 1024 * 1024))
    }

    override fun registerComponents(context: Context, glide: Glide) {
        // Nothing to see here!
    }
}
