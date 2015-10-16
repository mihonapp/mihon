package eu.kanade.mangafeed.data.helpers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import eu.kanade.mangafeed.data.caches.CacheManager;
import eu.kanade.mangafeed.sources.Batoto;
import eu.kanade.mangafeed.sources.MangaHere;
import eu.kanade.mangafeed.sources.Source;

public class SourceManager {

    public static final int BATOTO = 1;
    public static final int MANGAHERE = 2;

    private HashMap<Integer, Source> mSourcesMap;
    private NetworkHelper mNetworkHelper;
    private CacheManager mCacheManager;
    private Source selected;

    public SourceManager(NetworkHelper networkHelper, CacheManager cacheManager) {
        mSourcesMap = new HashMap<>();
        mNetworkHelper = networkHelper;
        mCacheManager = cacheManager;

        initializeSources();
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
            case MANGAHERE:
                return new MangaHere(mNetworkHelper, mCacheManager);
        }

        return null;
    }

    private void initializeSources() {
        mSourcesMap.put(BATOTO, createSource(BATOTO));
        mSourcesMap.put(MANGAHERE, createSource(MANGAHERE));
    }

    public List<Source> getSources() {
        return new ArrayList<Source>(mSourcesMap.values());
    }

    public void setSelectedSource(int sourceId) {
        selected = get(sourceId);
    }

    public Source getSelectedSource() {
        return selected;
    }
}
