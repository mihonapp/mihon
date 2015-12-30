package eu.kanade.mangafeed.ui.library.category;

import android.os.Bundle;

import java.util.List;

import javax.inject.Inject;

import eu.kanade.mangafeed.data.database.DatabaseHelper;
import eu.kanade.mangafeed.data.database.models.Category;
import eu.kanade.mangafeed.ui.base.presenter.BasePresenter;
import rx.android.schedulers.AndroidSchedulers;

public class CategoryPresenter extends BasePresenter<CategoryActivity> {

    @Inject DatabaseHelper db;

    private List<Category> categories;

    private static final int GET_CATEGORIES = 1;

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        restartableLatestCache(GET_CATEGORIES,
                () -> db.getCategories().createObservable()
                        .doOnNext(categories -> this.categories = categories)
                        .observeOn(AndroidSchedulers.mainThread()),
                CategoryActivity::setCategories);

        start(GET_CATEGORIES);
    }

    public void createCategory(String name) {
        Category cat = Category.create(name);

        // Set the new item in the last position
        int max = 0;
        if (categories != null) {
            for (Category cat2 : categories) {
                if (cat2.order > max) {
                    max = cat2.order + 1;
                }
            }
        }
        cat.order = max;

        db.insertCategory(cat).createObservable().subscribe();
    }

    public void deleteCategories(List<Category> categories) {
        db.deleteCategories(categories).createObservable().subscribe();
    }

    public void reorderCategories(List<Category> categories) {
        for (int i = 0; i < categories.size(); i++) {
            categories.get(i).order = i;
        }

        db.insertCategories(categories).createObservable().subscribe();
    }

    public void renameCategory(Category category, String name) {
        category.name = name;
        db.insertCategory(category).createObservable().subscribe();
    }
}
