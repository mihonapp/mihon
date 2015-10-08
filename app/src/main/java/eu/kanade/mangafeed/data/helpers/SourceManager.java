package eu.kanade.mangafeed.data.helpers;

import java.util.HashMap;

import eu.kanade.mangafeed.data.caches.CacheManager;
import eu.kanade.mangafeed.sources.Batoto;
import eu.kanade.mangafeed.sources.Source;

public class SourceManager {

    public static final int BATOTO = 1;

    private HashMap<Integer, Source> mSourcesMap;
    private NetworkHelper mNetworkHelper;
    private CacheManager mCacheManager;

    public SourceManager(NetworkHelper networkHelper, CacheManager cacheManager) {
        mSourcesMap = new HashMap<>();
        mNetworkHelper = networkHelper;
        mCacheManager = cacheManager;
    }

    public Source get(int sourceKey) {
        if (!mSourcesMap.containsKey(sourceKey)) {
            mSourcesMap.put(sourceKey, createSource(sourceKey));
        }
        return mSourcesMap.get(sourceKey);
    }

    private Source createSource(int sourceKey) {
        switch (sourceKey) {
            case BATOTO:
                return new Batoto(mNetworkHelper, mCacheManager);
        }

        return null;
    }
}
