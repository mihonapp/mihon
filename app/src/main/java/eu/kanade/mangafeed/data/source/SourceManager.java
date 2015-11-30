package eu.kanade.mangafeed.data.source;

import android.content.Context;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import eu.kanade.mangafeed.data.source.base.Source;
import eu.kanade.mangafeed.data.source.online.english.Batoto;
import eu.kanade.mangafeed.data.source.online.english.Kissmanga;
import eu.kanade.mangafeed.data.source.online.english.Mangafox;
import eu.kanade.mangafeed.data.source.online.english.Mangahere;

public class SourceManager {

    public static final int BATOTO = 1;
    public static final int MANGAHERE = 2;
    public static final int MANGAFOX = 3;
    public static final int KISSMANGA = 4;

    private HashMap<Integer, Source> mSourcesMap;
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
                return new Mangahere(context);
            case MANGAFOX:
                return new Mangafox(context);
            case KISSMANGA:
                return new Kissmanga(context);
        }

        return null;
    }

    private void initializeSources() {
        mSourcesMap.put(BATOTO, createSource(BATOTO));
        mSourcesMap.put(MANGAHERE, createSource(MANGAHERE));
        mSourcesMap.put(MANGAFOX, createSource(MANGAFOX));
        mSourcesMap.put(KISSMANGA, createSource(KISSMANGA));
    }

    public List<Source> getSources() {
        return new ArrayList<>(mSourcesMap.values());
    }

}
