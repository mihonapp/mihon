package eu.kanade.tachiyomi.event;

import eu.kanade.tachiyomi.data.database.models.Chapter;
import eu.kanade.tachiyomi.data.database.models.Manga;
import eu.kanade.tachiyomi.data.source.base.Source;

public class ReaderEvent {

    private Source source;
    private Manga manga;
    private Chapter chapter;

    public ReaderEvent(Source source, Manga manga, Chapter chapter) {
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
