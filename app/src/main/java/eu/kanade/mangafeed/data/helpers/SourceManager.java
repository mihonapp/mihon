package eu.kanade.mangafeed.data.helpers;

import android.content.Context;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import eu.kanade.mangafeed.data.caches.CacheManager;
import eu.kanade.mangafeed.sources.Batoto;
import eu.kanade.mangafeed.sources.MangaHere;
import eu.kanade.mangafeed.sources.base.Source;

public class SourceManager {

    public static final int BATOTO = 1;
    public static final int MANGAHERE = 2;

    private HashMap<Integer, Source> mSourcesMap;
    private NetworkHelper mNetworkHelper;
    private CacheManager mCacheManager;
    private Context context;

    public SourceManager(Context context) {
        mSourcesMap = new HashMap<>();
        this.context = context;

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
                return new Batoto(context);
            case MANGAHERE:
                return new MangaHere(context);
        }

        return null;
    }

    private void initializeSources() {
        mSourcesMap.put(BATOTO, createSource(BATOTO));
        mSourcesMap.put(MANGAHERE, createSource(MANGAHERE));
    }

    public List<Source> getSources() {
        return new ArrayList<>(mSourcesMap.values());
    }

}
