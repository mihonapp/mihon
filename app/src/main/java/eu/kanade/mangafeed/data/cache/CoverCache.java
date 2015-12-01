package eu.kanade.mangafeed.data.cache;

import android.content.Context;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.SimpleTarget;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import eu.kanade.mangafeed.util.DiskUtils;

public class CoverCache {

    private static final String PARAMETER_CACHE_DIRECTORY = "cover_disk_cache";

    private Context context;
    private File cacheDir;

    public CoverCache(Context context) {
        this.context = context;
        cacheDir = new File(context.getCacheDir(), PARAMETER_CACHE_DIRECTORY);
        createCacheDir();
    }

    private boolean createCacheDir() {
        return !cacheDir.exists() && cacheDir.mkdirs();
    }

    // Download the cover with Glide (it can avoid repeating requests) and save the file on this cache
    public void save(String cover, LazyHeaders headers) {
        GlideUrl url = new GlideUrl(cover, headers);
        Glide.with(context)
                .load(url)
                .downloadOnly(new SimpleTarget<File>() {
                    @Override
                    public void onResourceReady(File resource, GlideAnimation<? super File> anim) {
                        try {
                            add(cover, resource);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });
    }

    // Copy the cover from Glide's cache to this cache
    public void add(String key, File source) throws IOException {
        File dest = new File(cacheDir, DiskUtils.hashKeyForDisk(key));
        if (dest.exists())
            dest.delete();

        InputStream in = new FileInputStream(source);
        try {
            OutputStream out = new FileOutputStream(dest);
            try {
                // Transfer bytes from in to out
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

    // Get the cover from cache
    public File get(String key) {
        return new File(cacheDir, DiskUtils.hashKeyForDisk(key));
    }

    // Delete the cover from cache
    public boolean delete(String key) {
        File file = new File(cacheDir, DiskUtils.hashKeyForDisk(key));
        return file.exists() && file.delete();
    }

    // Load the cover from cache or network if it doesn't exist
    public void loadOrFetchInto(ImageView imageView, String cover, LazyHeaders headers) {
        File localCover = get(cover);
        if (localCover.exists()) {
            loadLocalInto(context, imageView, localCover);
        } else {
            loadRemoteInto(context, imageView, cover, headers);
        }
    }

    // Load the cover from cache
    public static void loadLocalInto(Context context, ImageView imageView, String cover) {
        File cacheDir = new File(context.getCacheDir(), PARAMETER_CACHE_DIRECTORY);
        File localCover = new File(cacheDir, DiskUtils.hashKeyForDisk(cover));
        if (localCover.exists()) {
            loadLocalInto(context, imageView, localCover);
        }
    }

    // Load the cover from the cache directory into the specified image view
    private static void loadLocalInto(Context context, ImageView imageView, File file) {
        Glide.with(context)
                .load(file)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .centerCrop()
                .into(imageView);
    }

    // Load the cover from network into the specified image view. It does NOT save the image in cache
    private static void loadRemoteInto(Context context, ImageView imageView, String cover, LazyHeaders headers) {
        GlideUrl url = new GlideUrl(cover, headers);
        Glide.with(context)
                .load(url)
                .diskCacheStrategy(DiskCacheStrategy.SOURCE)
                .centerCrop()
                .into(imageView);
    }

}
