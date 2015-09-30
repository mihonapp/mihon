package eu.kanade.mangafeed.presenter;

import android.content.Intent;

import javax.inject.Inject;

import eu.kanade.mangafeed.App;
import eu.kanade.mangafeed.data.helpers.DatabaseHelper;
import eu.kanade.mangafeed.data.helpers.PreferencesHelper;
import eu.kanade.mangafeed.data.models.Manga;
import eu.kanade.mangafeed.ui.activity.MangaDetailActivity;
import eu.kanade.mangafeed.view.LibraryView;
import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;
import timber.log.Timber;
import uk.co.ribot.easyadapter.EasyAdapter;

import static rx.android.schedulers.AndroidSchedulers.mainThread;

public class LibraryPresenter extends BasePresenter {

    private LibraryView view;

    @Inject
    DatabaseHelper db;

    @Inject
    PreferencesHelper prefs;

    private Subscription searchViewSubscription;
    private PublishSubject<Observable<String>> searchViewPublishSubject;

    public LibraryPresenter(LibraryView view) {
        this.view = view;
        App.getComponent(view.getActivity()).inject(this);

        //TODO remove, only for testing
        if (prefs.isFirstRun()) {
            db.manga.createDummyManga();
            db.chapter.createDummyChapters();
            prefs.setNotFirstRun();
        }

    }

    public void onMangaClick(EasyAdapter<Manga> adapter, int position) {
        Intent intent = MangaDetailActivity.newIntent(
                view.getActivity(),
                adapter.getItem(position)
        );
        view.getActivity().startActivity(intent);
    }

    public void initializeSearch() {
        searchViewPublishSubject = PublishSubject.create();
        searchViewSubscription = Observable.switchOnNext(searchViewPublishSubject)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(view.getAdapter().getFilter()::filter);
    }

    public void initializeMangas() {
        db.manga.get()
                .observeOn(mainThread())
                .subscribe(view::setMangas);
    }

    public void onQueryTextChange(String query) {
        if (searchViewPublishSubject != null) {
            searchViewPublishSubject.onNext(Observable.just(query));
        }
    }

}
