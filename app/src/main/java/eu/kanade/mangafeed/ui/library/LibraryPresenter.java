package eu.kanade.mangafeed.ui.library;

import android.os.Bundle;
import android.util.Pair;

import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import eu.kanade.mangafeed.data.cache.CoverCache;
import eu.kanade.mangafeed.data.database.DatabaseHelper;
import eu.kanade.mangafeed.data.database.models.Manga;
import eu.kanade.mangafeed.data.preference.PreferencesHelper;
import eu.kanade.mangafeed.data.source.SourceManager;
import eu.kanade.mangafeed.ui.base.presenter.BasePresenter;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

public class LibraryPresenter extends BasePresenter<LibraryFragment> {

    @Inject DatabaseHelper db;
    @Inject PreferencesHelper prefs;
    @Inject CoverCache coverCache;
    @Inject SourceManager sourceManager;

    private static final int GET_MANGAS = 1;

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        restartableLatestCache(GET_MANGAS,
                () -> db.getLibraryMangas().createObservable()
                        .observeOn(AndroidSchedulers.mainThread()),
                LibraryFragment::onNextMangas);

        start(GET_MANGAS);
    }

    public void deleteMangas(Observable<Manga> selectedMangas) {
        add(selectedMangas
                .subscribeOn(Schedulers.io())
                .doOnNext(manga -> manga.favorite = false)
                .toList()
                .flatMap(mangas -> db.insertMangas(mangas).createObservable())
                .subscribe());
    }

    public Observable<Map<Long, List<Manga>>> getLibraryMangasObservable() {
        return db.getLibraryMangas().createObservable()
                .flatMap(mangas -> Observable.from(mangas)
                        .groupBy(manga -> manga.category)
                        .flatMap(group -> group.toList().map(list -> new Pair<>(group.getKey(), list)))
                        .toMap(pair -> pair.first, pair -> pair.second));
    }

}
