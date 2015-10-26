package eu.kanade.mangafeed.presenter;

import android.os.Bundle;
import android.util.SparseBooleanArray;

import javax.inject.Inject;

import eu.kanade.mangafeed.data.helpers.DatabaseHelper;
import eu.kanade.mangafeed.data.helpers.PreferencesHelper;
import eu.kanade.mangafeed.ui.adapter.LibraryAdapter;
import eu.kanade.mangafeed.ui.fragment.LibraryFragment;
import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

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
            return;

        add(mFavoriteMangasSubscription = db.getMangasWithUnread()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(deliverLatestCache())
                .subscribe(this.split(LibraryFragment::onNextMangas)));
    }

    public void onDelete(SparseBooleanArray checkedItems, LibraryAdapter adapter) {
        if (mDeleteMangaSubscription != null)
            remove(mDeleteMangaSubscription);

        add(mDeleteMangaSubscription = Observable.range(0, checkedItems.size())
                .observeOn(Schedulers.io())
                .map(checkedItems::keyAt)
                .map(adapter::getItem)
                .map(manga -> {
                    manga.favorite = false;
                    return manga;
                })
                .toList()
                .flatMap(db::insertMangas)
                .subscribe());
    }

}
