package eu.kanade.mangafeed.event;

import eu.kanade.mangafeed.data.database.models.ChapterSync;

public class UpdateChapterSyncEvent {

    private ChapterSync chapterSync;

    public UpdateChapterSyncEvent(ChapterSync chapterSync) {
        this.chapterSync = chapterSync;
    }

    public ChapterSync getChapterSync() {
        return chapterSync;
    }

}
