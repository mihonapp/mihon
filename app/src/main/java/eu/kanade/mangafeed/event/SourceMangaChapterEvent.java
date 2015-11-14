package eu.kanade.mangafeed.event;

import eu.kanade.mangafeed.data.database.models.Chapter;
import eu.kanade.mangafeed.data.database.models.Manga;
import eu.kanade.mangafeed.data.source.base.Source;

public class SourceMangaChapterEvent {

    private Source source;
    private Manga manga;
    private Chapter chapter;

    public SourceMangaChapterEvent(Source source, Manga manga, Chapter chapter) {
        this.source = source;
        this.manga = manga;
        this.chapter = chapter;
    }

    public Source getSource() {
        return source;
    }

    public Manga getManga() {
        return manga;
    }

    public Chapter getChapter() {
        return chapter;
    }
}
