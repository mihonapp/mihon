package eu.kanade.mangafeed.data.chaptersync;

import rx.Observable;

public abstract class BaseChapterSync {

    // Name of the chapter sync service to display
    public abstract String getName();

    // Id of the sync service (must be declared and obtained from ChapterSyncManager to avoid conflicts)
    public abstract int getId();

    public abstract Observable<Boolean> login(String username, String password);

    public abstract boolean isLogged();
}
