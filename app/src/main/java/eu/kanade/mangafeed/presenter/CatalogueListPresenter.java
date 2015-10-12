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
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import uk.co.ribot.easyadapter.EasyAdapter;

public class CatalogueListPresenter extends BasePresenter {

    CatalogueListView view;
    EasyAdapter<Manga> adapter;
    Source selectedSource;

    @Inject SourceManager sourceManager;
    @Inject DatabaseHelper db;

    private Subscription mMangaFetchSubscription;


    public CatalogueListPresenter(CatalogueListView view) {
        this.view = view;
        App.getComponent(view.getActivity()).inject(this);
    }

    public void initialize() {
        int sourceId = view.getIntent().getIntExtra(Intent.EXTRA_UID, -1);
        selectedSource = sourceManager.get(sourceId);
        view.setSource(selectedSource);

        adapter = new EasyAdapter<>(view.getActivity(), CatalogueListHolder.class);
        view.setAdapter(adapter);
        view.setScrollListener();

        getMangasFromSource(1);
    }

    public void getMangasFromSource(int page) {
        subscriptions.remove(mMangaFetchSubscription);

        mMangaFetchSubscription = selectedSource.pullPopularMangasFromNetwork(page)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .flatMap(Observable::from)
                .map(this::networkToLocalManga)
                .toList()
                .subscribe(adapter::addItems);

        subscriptions.add(mMangaFetchSubscription);
    }

    private Manga networkToLocalManga(Manga networkManga) {
        Manga localManga = db.getMangaBlock(networkManga.url);
        if (localManga == null) {
            db.insertMangaBlock(networkManga);
            localManga = db.getMangaBlock(networkManga.url);
        }
        return localManga;
    }

}
