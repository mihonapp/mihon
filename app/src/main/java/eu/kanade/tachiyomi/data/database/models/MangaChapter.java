package eu.kanade.tachiyomi.data.database.models;

public class MangaChapter {

    public Manga manga;
    public Chapter chapter;

    public MangaChapter(Manga manga, Chapter chapter) {
        this.manga = manga;
        this.chapter = chapter;
    }
}
