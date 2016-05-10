package eu.kanade.tachiyomi.data.glide

import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.module.GlideModule
import eu.kanade.tachiyomi.App
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.network.NetworkHelper
import java.io.InputStream
import javax.inject.Inject

/**
 * Class used to update Glide module settings
 */
class AppGlideModule : GlideModule {

    @Inject lateinit var networkHelper: NetworkHelper

    override fun applyOptions(context: Context, builder: GlideBuilder) {
        // Set the cache size of Glide to 15 MiB
        builder.setDiskCache(InternalCacheDiskCacheFactory(context, 15 * 1024 * 1024))
    }

    override fun registerComponents(context: Context, glide: Glide) {
        App.get(context).component.inject(this)
        glide.register(GlideUrl::class.java, InputStream::class.java,
                OkHttpUrlLoader.Factory(networkHelper.defaultClient))
        glide.register(Manga::class.java, InputStream::class.java, MangaModelLoader.Factory())
    }
}
