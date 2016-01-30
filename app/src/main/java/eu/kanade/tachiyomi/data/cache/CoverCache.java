package eu.kanade.tachiyomi.data.cache;

import android.content.Context;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.signature.StringSignature;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import eu.kanade.tachiyomi.util.DiskUtils;

/**
 * Class used to create cover cache
 * It is used to store the covers of the library.
 * Makes use of Glide (which can avoid repeating requests) to download covers.
 * Names of files are created with the md5 of the thumbnail URL
 */
public class CoverCache {

    /**
     * Name of cache directory.
     */
    private static final String PARAMETER_CACHE_DIRECTORY = "cover_disk_cache";

    /**
     * Interface to global information about an application environment.
     */
    private final Context context;

    /**
     * Cache directory used for cache management.
     */
    private final File cacheDir;

    /**
     * Constructor of CoverCache.
     *
     * @param context application environment interface.
     */
    public CoverCache(Context context) {
        this.context = context;

        // Get cache directory from parameter.
        cacheDir = new File(context.getCacheDir(), PARAMETER_CACHE_DIRECTORY);

        // Create cache directory.
        createCacheDir();
    }

    /**
     * Create cache directory if it doesn't exist
     *
     * @return true if cache dir is created otherwise false.
     */
    private boolean createCacheDir() {
        return !cacheDir.exists() && cacheDir.mkdirs();
    }

    /**
     * Download the cover with Glide and save the file in this cache.
     *
     * @param thumbnailUrl url of thumbnail.
     * @param headers      headers included in Glide request.
     */
    public void save(String thumbnailUrl, LazyHeaders headers) {
        save(thumbnailUrl, headers, null);
    }

    /**
     * Download the cover with Glide and save the file.
     *
     * @param thumbnailUrl url of thumbnail.
     * @param headers      headers included in Glide request.
     * @param imageView    imageView where picture should be displayed.
     */
    private void save(String thumbnailUrl, LazyHeaders headers, @Nullable ImageView imageView) {
        // Check if url is empty.
        if (TextUtils.isEmpty(thumbnailUrl))
            return;

        // Download the cover with Glide and save the file.
        GlideUrl url = new GlideUrl(thumbnailUrl, headers);
        Glide.with(context)
                .load(url)
                .downloadOnly(new SimpleTarget<File>() {
                    @Override
                    public void onResourceReady(File resource, GlideAnimation<? super File> anim) {
                        try {
                            // Copy the cover from Glide's cache to local cache.
                            copyToLocalCache(thumbnailUrl, resource);

                            // Check if imageView isn't null and show picture in imageView.
                            if (imageView != null) {
                                loadFromCache(imageView, resource);
                            }
                        } catch (IOException e) {
                            // Do nothing.
                        }
                    }
                });
    }

    /**
     * Copy the cover from Glide's cache to this cache.
     *
     * @param thumbnailUrl url of thumbnail.
     * @param source       the cover image.
     * @throws IOException exception returned
     */
    public void copyToLocalCache(String thumbnailUrl, File source) throws IOException {
        // Create cache directory if needed.
        createCacheDir();

        // Get destination file.
        File dest = new File(cacheDir, DiskUtils.hashKeyForDisk(thumbnailUrl));

        // Delete the current file if it exists.
        if (dest.exists())
            dest.delete();

        // Write thumbnail image to file.
        InputStream in = new FileInputStream(source);
        try {
            OutputStream out = new FileOutputStream(dest);
            try {
                // Transfer bytes from in to out.
                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            } finally {
                out.close();
            }
        } finally {
            in.close();
        }
    }


    /**
     * Returns the cover from cache.
     *
     * @param thumbnailUrl the thumbnail url.
     * @return cover image.
     */
    private File getCoverFromCache(String thumbnailUrl) {
        return new File(cacheDir, DiskUtils.hashKeyForDisk(thumbnailUrl));
    }

    /**
     * Delete the cover file from the cache.
     *
     * @param thumbnailUrl the thumbnail url.
     * @return status of deletion.
     */
    public boolean deleteCoverFromCache(String thumbnailUrl) {
        // Check if url is empty.
        if (TextUtils.isEmpty(thumbnailUrl))
            return false;

        // Remove file.
        File file = new File(cacheDir, DiskUtils.hashKeyForDisk(thumbnailUrl));
        return file.exists() && file.delete();
    }

    /**
     * Save or load the image from cache
     *
     * @param imageView    imageView where picture should be displayed.
     * @param thumbnailUrl the thumbnail url.
     * @param headers      headers included in Glide request.
     */
    public void saveOrLoadFromCache(ImageView imageView, String thumbnailUrl, LazyHeaders headers) {
        // If file exist load it otherwise save it.
        File localCover = getCoverFromCache(thumbnailUrl);
        if (localCover.exists()) {
            loadFromCache(imageView, localCover);
        } else {
            save(thumbnailUrl, headers, imageView);
        }
    }

    /**
     * Helper method to load the cover from the cache directory into the specified image view.
     * Glide stores the resized image in its cache to improve performance.
     *
     * @param imageView imageView where picture should be displayed.
     * @param file      file to load. Must exist!.
     */
    private void loadFromCache(ImageView imageView, File file) {
        Glide.with(context)
                .load(file)
                .diskCacheStrategy(DiskCacheStrategy.RESULT)
                .centerCrop()
                .signature(new StringSignature(String.valueOf(file.lastModified())))
                .into(imageView);
    }

    /**
     * Helper method to load the cover from network into the specified image view.
     * The source image is stored in Glide's cache so that it can be easily copied to this cache
     * if the manga is added to the library.
     *
     * @param imageView    imageView where picture should be displayed.
     * @param thumbnailUrl url of thumbnail.
     * @param headers      headers included in Glide request.
     */
    public void loadFromNetwork(ImageView imageView, String thumbnailUrl, LazyHeaders headers) {
        // Check if url is empty.
        if (TextUtils.isEmpty(thumbnailUrl))
            return;

        GlideUrl url = new GlideUrl(thumbnailUrl, headers);
        Glide.with(context)
                .load(url)
                .diskCacheStrategy(DiskCacheStrategy.SOURCE)
                .centerCrop()
                .into(imageView);
    }

}
