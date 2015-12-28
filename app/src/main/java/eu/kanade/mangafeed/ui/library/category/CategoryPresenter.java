package eu.kanade.mangafeed.ui.library.category;

import android.os.Bundle;

import java.util.List;

import javax.inject.Inject;

import eu.kanade.mangafeed.data.database.DatabaseHelper;
import eu.kanade.mangafeed.data.database.models.Category;
import eu.kanade.mangafeed.ui.base.presenter.BasePresenter;
import rx.android.schedulers.AndroidSchedulers;

public class CategoryPresenter extends BasePresenter<CategoryFragment> {

    @Inject DatabaseHelper db;

    private static final int GET_CATEGORIES = 1;

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        restartableLatestCache(GET_CATEGORIES,
                () -> db.getCategories().createObservable()
                        .observeOn(AndroidSchedulers.mainThread()),
                CategoryFragment::setCategories);

        start(GET_CATEGORIES);
    }

    public void createCategory(String name) {
        db.insertCategory(Category.create(name)).createObservable().subscribe();
    }

    public void deleteCategories(List<Category> categories) {
        db.deleteCategories(categories).createObservable().subscribe();
    }
}
