package eu.kanade.mangafeed.presenter;

import android.content.Intent;

import javax.inject.Inject;

import eu.kanade.mangafeed.App;
import eu.kanade.mangafeed.data.helpers.DatabaseHelper;
import eu.kanade.mangafeed.data.helpers.SourceManager;
import eu.kanade.mangafeed.data.models.Manga;
import eu.kanade.mangafeed.sources.Source;
import eu.kanade.mangafeed.ui.adapter.CatalogueListHolder;
import eu.kanade.mangafeed.view.CatalogueListView;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import uk.co.ribot.easyadapter.EasyAdapter;

public class CatalogueListPresenter {

    CatalogueListView view;
    EasyAdapter<Manga> adapter;
    Source selectedSource;

    @Inject SourceManager sourceManager;
    @Inject DatabaseHelper db;


    public CatalogueListPresenter(CatalogueListView view) {
        this.view = view;
        App.getComponent(view.getActivity()).inject(this);
    }

    public void initializeSource() {
        int sourceId = view.getIntent().getIntExtra(Intent.EXTRA_UID, -1);
        selectedSource = sourceManager.get(sourceId);
        view.setSource(selectedSource);

        adapter = new EasyAdapter<>(view.getActivity(), CatalogueListHolder.class);
        view.setAdapter(adapter);

        getMangasFromSource();
    }

    private void getMangasFromSource() {
        selectedSource.pullPopularMangasFromNetwork(1)
                .subscribeOn(Schedulers.io())
                .flatMap(Observable::from)
                .flatMap(networkManga -> db.getManga(networkManga.url)
                        .flatMap(result -> {
                            if (result.size() == 0) {
                                return db.insertManga(networkManga)
                                        .flatMap(i -> Observable.just(networkManga));
                            }
                            return Observable.just(networkManga);
                        }))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(adapter::addItem);
    }

}
