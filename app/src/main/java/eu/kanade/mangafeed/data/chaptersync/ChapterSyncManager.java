package eu.kanade.mangafeed.data.chaptersync;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

public class ChapterSyncManager {

    private List<BaseChapterSync> services;
    private MyAnimeList myAnimeList;

    public static final int MYANIMELIST = 1;

    public ChapterSyncManager(Context context) {
        services = new ArrayList<>();
        myAnimeList = new MyAnimeList(context);
        services.add(myAnimeList);
    }

    public MyAnimeList getMyAnimeList() {
        return myAnimeList;
    }

    public List<BaseChapterSync> getChapterSyncServices() {
        return services;
    }

}
