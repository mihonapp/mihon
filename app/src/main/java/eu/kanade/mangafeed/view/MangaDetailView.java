package eu.kanade.mangafeed.view;

import java.util.List;

import eu.kanade.mangafeed.data.models.Chapter;
import eu.kanade.mangafeed.data.models.Manga;

public interface MangaDetailView extends BaseView {

    void loadManga(Manga manga);
    void setChapters(List<Chapter> chapters);
}
