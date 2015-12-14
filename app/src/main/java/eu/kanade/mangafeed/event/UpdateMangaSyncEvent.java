package eu.kanade.mangafeed.event;

import eu.kanade.mangafeed.data.database.models.MangaSync;

public class UpdateMangaSyncEvent {

    private MangaSync mangaSync;

    public UpdateMangaSyncEvent(MangaSync mangaSync) {
        this.mangaSync = mangaSync;
    }

    public MangaSync getMangaSync() {
        return mangaSync;
    }

}
