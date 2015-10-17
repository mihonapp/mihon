package eu.kanade.mangafeed.presenter;

import android.os.Bundle;
import android.util.SparseBooleanArray;

import javax.inject.Inject;

import eu.kanade.mangafeed.data.helpers.DatabaseHelper;
import eu.kanade.mangafeed.data.helpers.PreferencesHelper;
import eu.kanade.mangafeed.data.models.Manga;
import eu.kanade.mangafeed.ui.fragment.LibraryFragment;
import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import uk.co.ribot.easyadapter.EasyAdapter;

public class LibraryPresenter extends BasePresenter<LibraryFragment> {

    @Inject DatabaseHelper db;
    @Inject PreferencesHelper prefs;

    private Subscription mFavoriteMangasSubscription;
    private Subscription mDeleteMangaSubscription;

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);
    }

    @Override
    protected void onTakeView(LibraryFragment view) {
        super.onTakeView(view);
        getFavoriteMangas();
    }

    public void getFavoriteMangas() {
        if (mFavoriteMangasSubscription != null)
            remove(mFavoriteMangasSubscription);

        mFavoriteMangasSubscription = db.getMangasWithUnread()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(deliverLatestCache())
                .subscribe(this.split((view, mangas) -> {
                    view.getAdapter().setNewItems(mangas);
                }));

        add(mFavoriteMangasSubscription);
    }

    public void onDelete(SparseBooleanArray checkedItems, EasyAdapter<Manga> adapter) {
        remove(mDeleteMangaSubscription);

        mDeleteMangaSubscription = Observable.range(0, checkedItems.size())
                .observeOn(Schedulers.io())
                .map(checkedItems::keyAt)
                .map(adapter::getItem)
                .toList()
                .flatMap(db::deleteMangas)
                .subscribe();

        add(mDeleteMangaSubscription);
    }

}
