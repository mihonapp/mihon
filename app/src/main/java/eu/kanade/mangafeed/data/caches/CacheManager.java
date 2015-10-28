package eu.kanade.mangafeed.data.caches;

import android.content.Context;

import com.bumptech.glide.Glide;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.jakewharton.disklrucache.DiskLruCache;
import com.squareup.okhttp.Response;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.util.List;

import eu.kanade.mangafeed.data.models.Page;
import eu.kanade.mangafeed.util.DiskUtils;
import okio.BufferedSink;
import okio.Okio;
import rx.Observable;

public class CacheManager {

    private static final String PARAMETER_CACHE_DIRECTORY = "chapter_disk_cache";
    private static final int PARAMETER_APP_VERSION = 1;
    private static final int PARAMETER_VALUE_COUNT = 1;
    private static final long PARAMETER_CACHE_SIZE = 100 * 1024 * 1024;
    private static final int READ_TIMEOUT = 60;

    private Context mContext;
    private Gson mGson;

    private DiskLruCache mDiskCache;

    public CacheManager(Context context) {
        mContext = context;
        mGson = new Gson();

        try {
            mDiskCache = DiskLruCache.open(
                    new File(context.getCacheDir(), PARAMETER_CACHE_DIRECTORY),
                    PARAMETER_APP_VERSION,
                    PARAMETER_VALUE_COUNT,
                    PARAMETER_CACHE_SIZE
            );
        } catch (IOException e) {
            // Do Nothing.
        }
    }

    public Observable<Boolean> clearImageCache() {
        return Observable.create(subscriber -> {
            try {
                subscriber.onNext(clearImageCacheImpl());
                subscriber.onCompleted();
            } catch (Throwable e) {
                subscriber.onError(e);
            }
        });
    }

    private boolean clearImageCacheImpl() {
        boolean isSuccessful = true;

        File imageCacheDirectory = Glide.getPhotoCacheDir(mContext);
        if (imageCacheDirectory.isDirectory()) {
            for (File cachedFile : imageCacheDirectory.listFiles()) {
                if (!cachedFile.delete()) {
                    isSuccessful = false;
                }
            }
        } else {
            isSuccessful = false;
        }

        File urlCacheDirectory = getCacheDir();
        if (urlCacheDirectory.isDirectory()) {
            for (File cachedFile : urlCacheDirectory.listFiles()) {
                if (!cachedFile.delete()) {
                    isSuccessful = false;
                }
            }
        } else {
            isSuccessful = false;
        }

        return isSuccessful;
    }

    public Observable<List<Page>> getPageUrlsFromDiskCache(final String chapterUrl) {
        return Observable.create(subscriber -> {
            try {
                List<Page> pages = getPageUrlsFromDiskCacheImpl(chapterUrl);
                subscriber.onNext(pages);
                subscriber.onCompleted();
            } catch (Throwable e) {
                subscriber.onError(e);
            }
        });
    }

    private List<Page> getPageUrlsFromDiskCacheImpl(String chapterUrl) throws IOException {
        DiskLruCache.Snapshot snapshot = null;
        List<Page> pages = null;

        try {
            String key = DiskUtils.hashKeyForDisk(chapterUrl);
            snapshot = mDiskCache.get(key);

            Type collectionType = new TypeToken<List<Page>>() {}.getType();
            pages = mGson.fromJson(snapshot.getString(0), collectionType);
        } catch (IOException e) {
            // Do Nothing.
        } finally {
            if (snapshot != null) {
                snapshot.close();
            }
        }
        return pages;
    }

    public void putPageUrlsToDiskCache(final String chapterUrl, final List<Page> pages) {
        String cachedValue = mGson.toJson(pages);

        DiskLruCache.Editor editor = null;
        OutputStream outputStream = null;
        try {
            String key = DiskUtils.hashKeyForDisk(chapterUrl);
            editor = mDiskCache.edit(key);
            if (editor == null) {
                return;
            }

            outputStream = new BufferedOutputStream(editor.newOutputStream(0));
            outputStream.write(cachedValue.getBytes());
            outputStream.flush();

            mDiskCache.flush();
            editor.commit();
        } catch (Exception e) {
            // Do Nothing.
        } finally {
            if (editor != null) {
                editor.abortUnlessCommitted();
            }
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException ignore) {
                    // Do Nothing.
                }
            }
        }
    }

    public File getCacheDir() {
        return mDiskCache.getDirectory();
    }

    public boolean isImageInCache(final String imageUrl) {
        try {
            return mDiskCache.get(DiskUtils.hashKeyForDisk(imageUrl)) != null;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    public String getImagePath(final String imageUrl) {
        try {
            String imageName = DiskUtils.hashKeyForDisk(imageUrl) + ".0";
            File file = new File(mDiskCache.getDirectory(), imageName);
            return file.getCanonicalPath();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean putImageToDiskCache(final String imageUrl, final Response response) {
        DiskLruCache.Editor editor = null;
        BufferedSink sink = null;

        try {
            String key = DiskUtils.hashKeyForDisk(imageUrl);
            editor = mDiskCache.edit(key);
            if (editor == null) {
                return false;
            }

            OutputStream outputStream = new BufferedOutputStream(editor.newOutputStream(0));
            sink = Okio.buffer(Okio.sink(outputStream));
            sink.writeAll(response.body().source());
            sink.flush();

            mDiskCache.flush();
            editor.commit();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } finally {
            if (editor != null) {
                editor.abortUnlessCommitted();
            }
            if (sink != null) {
                try {
                    sink.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return true;
    }

}

