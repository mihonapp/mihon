package eu.kanade.mangafeed.view;

import java.util.ArrayList;
import java.util.List;

import eu.kanade.mangafeed.data.models.Manga;
import eu.kanade.mangafeed.ui.adapter.CatalogueArrayAdapter;

public interface LibraryView extends BaseView {

    void setMangas(List<Manga> mangas);
    CatalogueArrayAdapter getAdapter();
}
