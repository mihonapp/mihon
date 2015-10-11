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
import rx.schedulers.Schedulers;

public class LibraryPresenter extends BasePresenter {

    private LibraryView view;

    @Inject DatabaseHelper db;
    @Inject PreferencesHelper prefs;

    LibraryAdapter<Manga> adapter;

    public LibraryPresenter(LibraryView view) {
        this.view = view;
        App.getComponent(view.getActivity()).inject(this);

        //TODO remove, only for testing
        if (prefs.isFirstRun()) {
            db.insertMangas(DummyDataUtil.createDummyManga()).toBlocking().single();
            db.insertChapters(DummyDataUtil.createDummyChapters()).subscribe();
            prefs.setNotFirstRun();
        }

    }

    public void onMangaClick(int position) {
        Intent intent = MangaDetailActivity.newIntent(
                view.getActivity(),
                adapter.getItem(position)
        );
        view.getActivity().startActivity(intent);
    }

    public void initializeMangas() {
        adapter = new LibraryAdapter<>(view.getActivity());
        view.setAdapter(adapter);
        view.setMangaClickListener();

        subscriptions.add(db.getMangasWithUnread()
                        .subscribe(adapter::setNewItems)
        );

    }

    public void onQueryTextChange(String query) {
        adapter.getFilter().filter(query);
    }

    public void onDelete(SparseBooleanArray checkedItems) {
        Observable.range(0, checkedItems.size())
                .observeOn(Schedulers.io())
                .map(checkedItems::keyAt)
                .map(adapter::getItem)
                .toList()
                .flatMap(db::deleteMangas)
                .subscribe();
    }

}
