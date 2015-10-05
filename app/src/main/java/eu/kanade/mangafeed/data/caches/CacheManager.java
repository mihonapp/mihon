package eu.kanade.mangafeed.data.caches;

import android.content.Context;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.FutureTarget;
import com.bumptech.glide.request.target.Target;
import com.jakewharton.disklrucache.DiskLruCache;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import eu.kanade.mangafeed.util.DiskUtils;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Action0;

public class CacheManager {

    private static final String PARAMETER_CACHE_DIRECTORY = "chapter_disk_cache";
    private static final int PARAMETER_APP_VERSION = 1;
    private static final int PARAMETER_VALUE_COUNT = 1;
    private static final long PARAMETER_CACHE_SIZE = 10 * 1024 * 1024;
    private static final int READ_TIMEOUT = 60;

    private Context mContext;

    private DiskLruCache mDiskCache;

    public CacheManager(Context context) {
        mContext = context;

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

    public Observable<File> cacheImagesFromUrls(final List<String> imageUrls) {
        return Observable.create(new Observable.OnSubscribe<File>() {
            @Override
            public void call(Subscriber<? super File> subscriber) {
                try {
                    for (String imageUrl : imageUrls) {
                        if (!subscriber.isUnsubscribed()) {
                            subscriber.onNext(cacheImageFromUrl(imageUrl));
                        }
                    }
                    subscriber.onCompleted();
                } catch (Throwable e) {
                    subscriber.onError(e);
                }
            }
        });
    }

    private File cacheImageFromUrl(String imageUrl) throws InterruptedException, ExecutionException, TimeoutException {
        FutureTarget<File> cacheFutureTarget = Glide.with(mContext)
                .load(imageUrl)
                .downloadOnly(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL);

        return cacheFutureTarget.get(READ_TIMEOUT, TimeUnit.SECONDS);
    }

    public Observable<Boolean> clearImageCache() {
        return Observable.create(new Observable.OnSubscribe<Boolean>() {
            @Override
            public void call(Subscriber<? super Boolean> subscriber) {
                try {
                    subscriber.onNext(clearImageCacheImpl());
                    subscriber.onCompleted();
                } catch (Throwable e) {
                    subscriber.onError(e);
                }
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

    public Observable<String> getImageUrlsFromDiskCache(final String chapterUrl) {
        return Observable.create(new Observable.OnSubscribe<String>() {
            @Override
            public void call(Subscriber<? super String> subscriber) {
                try {
                    String[] imageUrls = getImageUrlsFromDiskCacheImpl(chapterUrl);

                    for (String imageUrl : imageUrls) {
                        if (!subscriber.isUnsubscribed()) {
                            subscriber.onNext(imageUrl);
                        }
                    }
                    subscriber.onCompleted();
                } catch (Throwable e) {
                    subscriber.onError(e);
                }
            }
        });
    }

    private String[] getImageUrlsFromDiskCacheImpl(String chapterUrl) throws IOException {
        DiskLruCache.Snapshot snapshot = null;

        try {
            String key = DiskUtils.hashKeyForDisk(chapterUrl);

            snapshot = mDiskCache.get(key);

            String joinedImageUrls = snapshot.getString(0);
            return joinedImageUrls.split(",");
        } finally {
            if (snapshot != null) {
                snapshot.close();
            }
        }
    }

    public Action0 putImageUrlsToDiskCache(final String chapterUrl, final List<String> imageUrls) {
        return new Action0() {
            @Override
            public void call() {
                try {
                    putImageUrlsToDiskCacheImpl(chapterUrl, imageUrls);
                } catch (IOException e) {
                    // Do Nothing.
                }
            }
        };
    }

    private void putImageUrlsToDiskCacheImpl(String chapterUrl, List<String> imageUrls) throws IOException {
        String cachedValue = joinImageUrlsToCacheValue(imageUrls);

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
        } finally {
            if (editor != null) {
                try {
                    editor.abort();
                } catch (IOException ignore) {
                    // Do Nothing.
                }
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

    private String joinImageUrlsToCacheValue(List<String> imageUrls) {
        StringBuilder stringBuilder = new StringBuilder();
        for (int index = 0; index < imageUrls.size(); index++) {
            if (index == 0) {
                stringBuilder.append(imageUrls.get(index));
            } else {
                stringBuilder.append(",");
                stringBuilder.append(imageUrls.get(index));
            }
        }

        return stringBuilder.toString();
    }

    public File getCacheDir() {
        return mDiskCache.getDirectory();
    }
}

