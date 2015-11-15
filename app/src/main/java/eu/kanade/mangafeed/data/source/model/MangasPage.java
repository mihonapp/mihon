package eu.kanade.mangafeed.data.source.model;

import java.util.List;

import eu.kanade.mangafeed.data.database.models.Manga;

public class MangasPage {

    public List<Manga> mangas;
    public int page;
    public String url;
    public String nextPageUrl;

    public MangasPage(int page) {
        this.page = page;
    }

}
