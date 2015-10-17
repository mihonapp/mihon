package eu.kanade.mangafeed.presenter;

import eu.kanade.mangafeed.App;
import eu.kanade.mangafeed.data.models.Manga;
import eu.kanade.mangafeed.view.MangaCatalogueView;

public class MangaCataloguePresenter extends BasePresenter {

    private MangaCatalogueView view;
    private Manga manga;

    public MangaCataloguePresenter(MangaCatalogueView view) {
        this.view = view;
        App.getComponent(view.getActivity()).inject(this);
    }

    public void initialize() {

    }

    public void onEventMainThread(Manga manga) {
        this.manga = manga;
        initializeManga();
    }

    private void initializeManga() {
        view.setTitle(manga.title);
        view.setMangaInformation(manga);
    }
}
