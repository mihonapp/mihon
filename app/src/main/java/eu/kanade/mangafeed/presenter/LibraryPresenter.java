package eu.kanade.mangafeed.presenter;

import android.content.Intent;

import javax.inject.Inject;

import eu.kanade.mangafeed.App;
import eu.kanade.mangafeed.data.helpers.DatabaseHelper;
import eu.kanade.mangafeed.data.helpers.PreferencesHelper;
import eu.kanade.mangafeed.data.models.Manga;
import eu.kanade.mangafeed.ui.activity.MangaDetailActivity;
import eu.kanade.mangafeed.ui.adapter.LibraryAdapter;
import eu.kanade.mangafeed.view.LibraryView;
import rx.Subscription;

import static rx.android.schedulers.AndroidSchedulers.mainThread;

public class LibraryPresenter extends BasePresenter {

    private LibraryView view;

    @Inject DatabaseHelper db;
    @Inject PreferencesHelper prefs;

    LibraryAdapter<Manga> adapter;
    private Subscription mangaListSubscription;

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

    public void onMangaClick(int position) {
        Intent intent = MangaDetailActivity.newIntent(
                view.getActivity(),
                adapter.getItem(position)
        );
        view.getActivity().startActivity(intent);
    }

    public void initializeMangas() {
        mangaListSubscription = db.manga.getWithUnread()
                .observeOn(mainThread())
                .subscribe(mangas -> {
                    adapter = new LibraryAdapter<>(view.getActivity(), mangas);
                    view.setAdapter(adapter);
                });
    }

    public void onQueryTextChange(String query) {
        adapter.getFilter().filter(query);
    }

    public void destroySubscriptions() {
        if (mangaListSubscription != null) {
            mangaListSubscription.unsubscribe();
        }
    }

}
