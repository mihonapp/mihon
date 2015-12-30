package eu.kanade.mangafeed.ui.library;

import android.os.Bundle;
import android.util.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import de.greenrobot.event.EventBus;
import eu.kanade.mangafeed.data.cache.CoverCache;
import eu.kanade.mangafeed.data.database.DatabaseHelper;
import eu.kanade.mangafeed.data.database.models.Category;
import eu.kanade.mangafeed.data.database.models.Manga;
import eu.kanade.mangafeed.data.database.models.MangaCategory;
import eu.kanade.mangafeed.data.preference.PreferencesHelper;
import eu.kanade.mangafeed.data.source.SourceManager;
import eu.kanade.mangafeed.event.LibraryMangasEvent;
import eu.kanade.mangafeed.ui.base.presenter.BasePresenter;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;

public class LibraryPresenter extends BasePresenter<LibraryFragment> {

    @Inject DatabaseHelper db;
    @Inject PreferencesHelper preferences;
    @Inject CoverCache coverCache;
    @Inject SourceManager sourceManager;

    protected List<Category> categories;
    protected List<Manga> selectedMangas;

    private static final int GET_LIBRARY = 1;

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        selectedMangas = new ArrayList<>();

        restartableLatestCache(GET_LIBRARY,
                this::getLibraryObservable,
                (view, pair) -> view.onNextLibraryUpdate(pair.first, pair.second));

        start(GET_LIBRARY);
    }

    @Override
    protected void onDestroy() {
        EventBus.getDefault().removeStickyEvent(LibraryMangasEvent.class);
        super.onDestroy();
    }

    private Observable<Pair<List<Category>, Map<Integer, List<Manga>>>> getLibraryObservable() {
        return Observable.combineLatest(getCategoriesObservable(), getLibraryMangasObservable(),
                Pair::create)
                .observeOn(AndroidSchedulers.mainThread());
    }

    private Observable<List<Category>> getCategoriesObservable() {
        return db.getCategories().createObservable()
                .doOnNext(categories -> this.categories = categories);
    }

    private Observable<Map<Integer, List<Manga>>> getLibraryMangasObservable() {
        return db.getLibraryMangas().createObservable()
                .flatMap(mangas -> Observable.from(mangas)
                        .groupBy(manga -> manga.category)
                        .flatMap(group -> group.toList()
                                .map(list -> Pair.create(group.getKey(), list)))
                        .toMap(pair -> pair.first, pair -> pair.second));
    }

    public void setSelection(Manga manga, boolean selected) {
        if (selected) {
            selectedMangas.add(manga);
        } else {
            selectedMangas.remove(manga);
        }
    }

    public String[] getCategoriesNames() {
        int count = categories.size();
        String[] names = new String[count];

        for (int i = 0; i < count; i++) {
            names[i] = categories.get(i).name;
        }

        return names;
    }

    public void deleteMangas() {
        for (Manga manga : selectedMangas) {
            manga.favorite = false;
        }

        db.insertMangas(selectedMangas).executeAsBlocking();
    }

    public void moveMangasToCategories(Integer[] positions, List<Manga> mangas) {
        List<Category> categoriesToAdd = new ArrayList<>();
        for (Integer index : positions) {
            categoriesToAdd.add(categories.get(index));
        }

        moveMangasToCategories(categoriesToAdd, mangas);
    }

    public void moveMangasToCategories(List<Category> categories, List<Manga> mangas) {
        List<MangaCategory> mc = new ArrayList<>();

        for (Manga manga : mangas) {
            for (Category cat : categories) {
                mc.add(MangaCategory.create(manga, cat));
            }
        }

        db.setMangaCategories(mc, mangas);
    }
}
