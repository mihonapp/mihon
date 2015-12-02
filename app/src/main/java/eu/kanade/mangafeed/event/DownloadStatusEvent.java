package eu.kanade.mangafeed.event;

import eu.kanade.mangafeed.data.database.models.Chapter;

public class DownloadStatusEvent {

    private Chapter chapter;
    private int status;

    public DownloadStatusEvent(Chapter chapter, int status) {
        this.chapter = chapter;
        this.status = status;
    }

    public Chapter getChapter() {
        return chapter;
    }

    public int getStatus() {
        return status;
    }

}
