package eu.kanade.mangafeed.presenter;

import android.content.Intent;

import java.util.ArrayList;

import javax.inject.Inject;

import de.greenrobot.event.EventBus;
import eu.kanade.mangafeed.App;
import eu.kanade.mangafeed.data.helpers.DatabaseHelper;
import eu.kanade.mangafeed.ui.activity.MangaDetailActivity;
import eu.kanade.mangafeed.view.LibraryView;
import uk.co.ribot.easyadapter.EasyAdapter;

import static rx.android.schedulers.AndroidSchedulers.mainThread;

public class LibraryPresenter {

    private LibraryView mLibraryView;

    @Inject
    public DatabaseHelper db;

    public LibraryPresenter(LibraryView libraryView) {
        mLibraryView = libraryView;
        App.getComponent(libraryView.getActivity()).inject(this);
    }

    public void onMangaClick(EasyAdapter adapter, int position) {
        Intent intent = new Intent(mLibraryView.getActivity(), MangaDetailActivity.class);
        EventBus.getDefault().postSticky(adapter.getItem(position));
        mLibraryView.getActivity().startActivity(intent);
    }

    public void initializeMangas() {
        db.manga.get()
                .observeOn(mainThread())
                .subscribe(
                        mangas -> {
                            mLibraryView.setMangas(new ArrayList<>(mangas));
                        }
                );
    }

}
