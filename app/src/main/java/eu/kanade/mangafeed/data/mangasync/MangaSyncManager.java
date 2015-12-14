package eu.kanade.mangafeed.data.mangasync;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

import eu.kanade.mangafeed.data.mangasync.base.BaseMangaSync;
import eu.kanade.mangafeed.data.mangasync.services.MyAnimeList;

public class MangaSyncManager {

    private List<BaseMangaSync> services;
    private MyAnimeList myAnimeList;

    public static final int MYANIMELIST = 1;

    public MangaSyncManager(Context context) {
        services = new ArrayList<>();
        myAnimeList = new MyAnimeList(context);
        services.add(myAnimeList);
    }

    public MyAnimeList getMyAnimeList() {
        return myAnimeList;
    }

    public List<BaseMangaSync> getSyncServices() {
        return services;
    }

    public BaseMangaSync getSyncService(int id) {
        switch (id) {
            case MYANIMELIST:
                return myAnimeList;
        }
        return null;
    }

}
