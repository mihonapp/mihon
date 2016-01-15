package eu.kanade.tachiyomi.data.mangasync;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

import eu.kanade.tachiyomi.data.mangasync.base.MangaSyncService;
import eu.kanade.tachiyomi.data.mangasync.services.MyAnimeList;

public class MangaSyncManager {

    private List<MangaSyncService> services;
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

    public List<MangaSyncService> getSyncServices() {
        return services;
    }

    public MangaSyncService getSyncService(int id) {
        switch (id) {
            case MYANIMELIST:
                return myAnimeList;
        }
        return null;
    }

}
