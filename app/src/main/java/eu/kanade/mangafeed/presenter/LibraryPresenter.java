package eu.kanade.mangafeed.presenter;

import android.content.Intent;
import android.util.SparseBooleanArray;

import javax.inject.Inject;

import eu.kanade.mangafeed.App;
import eu.kanade.mangafeed.data.helpers.DatabaseHelper;
import eu.kanade.mangafeed.data.helpers.PreferencesHelper;
import eu.kanade.mangafeed.data.models.Manga;
import eu.kanade.mangafeed.ui.activity.MangaDetailActivity;
import eu.kanade.mangafeed.ui.adapter.LibraryAdapter;
import eu.kanade.mangafeed.util.DummyDataUtil;
import eu.kanade.mangafeed.view.LibraryView;
import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

public class LibraryPresenter extends BasePresenter {

    private LibraryView view;

    @Inject DatabaseHelper db;
    @Inject PreferencesHelper prefs;

    LibraryAdapter<Manga> adapter;

    private Subscription mFavoriteMangasSubscription;
    private Subscription mDeleteMangaSubscription;

    public LibraryPresenter(LibraryView view) {
        this.view = view;
        App.getComponent(view.getActivity()).inject(this);
    }

    public void onMangaClick(int position) {
        Intent intent = MangaDetailActivity.newIntent(
                view.getActivity(),
                adapter.getItem(position)
        );
        view.getActivity().startActivity(intent);
    }

    public void initialize() {
        adapter = new LibraryAdapter<>(view.getActivity());
        view.setAdapter(adapter);
        view.setMangaClickListener();

        getFavoriteMangas();
    }

    public void getFavoriteMangas() {
        subscriptions.remove(mFavoriteMangasSubscription);

        mFavoriteMangasSubscription = db.getMangasWithUnread()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(adapter::setNewItems);

        subscriptions.add(mFavoriteMangasSubscription);
    }

    public void onQueryTextChange(String query) {
        adapter.getFilter().filter(query);
    }

    public void onDelete(SparseBooleanArray checkedItems) {
        subscriptions.remove(mDeleteMangaSubscription);

        mDeleteMangaSubscription = Observable.range(0, checkedItems.size())
                .observeOn(Schedulers.io())
                .map(checkedItems::keyAt)
                .map(adapter::getItem)
                .toList()
                .flatMap(db::deleteMangas)
                .subscribe();

        subscriptions.add(mDeleteMangaSubscription);
    }

}
