package eu.kanade.mangafeed.util.events;

import eu.kanade.mangafeed.data.models.Chapter;
import eu.kanade.mangafeed.sources.Source;

public class SourceChapterEvent {

    private Source source;
    private Chapter chapter;

    public SourceChapterEvent(Source source, Chapter chapter) {
        this.source = source;
        this.chapter = chapter;
    }

    public Source getSource() {
        return source;
    }

    public Chapter getChapter() {
        return chapter;
    }
}
