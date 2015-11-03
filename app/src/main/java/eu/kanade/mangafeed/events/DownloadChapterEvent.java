package eu.kanade.mangafeed.events;

import eu.kanade.mangafeed.data.models.Chapter;
import eu.kanade.mangafeed.data.models.Manga;

public class DownloadChapterEvent {
    private Manga manga;
    private Chapter chapter;

    public DownloadChapterEvent(Manga manga, Chapter chapter) {
        this.manga = manga;
        this.chapter = chapter;
    }

    public Manga getManga() {
        return manga;
    }

    public Chapter getChapter() {
        return chapter;
    }
}
