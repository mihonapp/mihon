package eu.kanade.tachiyomi.data.cache;

import android.content.Context;

import com.bumptech.glide.Glide;
import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory;
import com.bumptech.glide.module.GlideModule;

/**
 * Class used to update Glide module settings
 */
public class CoverGlideModule implements GlideModule {

    @Override
    public void applyOptions(Context context, GlideBuilder builder) {
        // Bitmaps decoded from most image formats (other than GIFs with hidden configs)
        // will be decoded with the ARGB_8888 config.
        builder.setDecodeFormat(DecodeFormat.PREFER_ARGB_8888);

        // Set the cache size of Glide to 15 MiB
        builder.setDiskCache(new InternalCacheDiskCacheFactory(context, 15 * 1024 * 1024));
    }

    @Override
    public void registerComponents(Context context, Glide glide) {
        // Nothing to see here!
    }
}
