package eu.kanade.mangafeed.view;

import uk.co.ribot.easyadapter.EasyAdapter;

public interface LibraryView extends BaseView {

    void setAdapter(EasyAdapter mangas);
    void setMangaClickListener();
}
