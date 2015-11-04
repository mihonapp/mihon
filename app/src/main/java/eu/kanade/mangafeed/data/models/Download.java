package eu.kanade.mangafeed.data.models;

import java.io.File;
import java.util.List;

import eu.kanade.mangafeed.sources.base.Source;

public class Download {
    public Source source;
    public Manga manga;
    public Chapter chapter;
    public List<Page> pages;
    public File directory;

    public Download(Source source, Manga manga, Chapter chapter) {
        this.source = source;
        this.manga = manga;
        this.chapter = chapter;
    }
}