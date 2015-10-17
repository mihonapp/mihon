package eu.kanade.mangafeed.view;

import eu.kanade.mangafeed.data.models.Manga;

public interface MangaCatalogueView extends BaseView {
    void setTitle(String title);
    void setMangaInformation(Manga manga);
}
