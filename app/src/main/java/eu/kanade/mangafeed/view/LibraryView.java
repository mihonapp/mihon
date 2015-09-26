package eu.kanade.mangafeed.view;

import java.util.ArrayList;

import eu.kanade.mangafeed.data.models.Manga;

public interface LibraryView extends BaseView {

    void setMangas(ArrayList<Manga> mangas);
}
