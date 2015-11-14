package eu.kanade.mangafeed.event;

import java.util.List;

import eu.kanade.mangafeed.data.database.models.Chapter;
import eu.kanade.mangafeed.data.database.models.Manga;

public class DownloadChaptersEvent {
    private Manga manga;
    private List<Chapter> chapters;

    public DownloadChaptersEvent(Manga manga, List<Chapter> chapters) {
        this.manga = manga;
        this.chapters = chapters;
    }

    public Manga getManga() {
        return manga;
    }

    public List<Chapter> getChapters() {
        return chapters;
    }
}
