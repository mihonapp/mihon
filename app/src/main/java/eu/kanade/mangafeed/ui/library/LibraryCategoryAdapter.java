package eu.kanade.mangafeed.ui.library;

import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;

import java.util.List;
import java.util.Map;

import eu.kanade.mangafeed.data.database.models.Category;
import eu.kanade.mangafeed.data.database.models.Manga;
import eu.kanade.mangafeed.ui.reader.viewer.common.SmartFragmentStatePagerAdapter;

class LibraryCategoryAdapter extends SmartFragmentStatePagerAdapter {

    private LibraryFragment fragment;
    private List<Category> categories;
    private Map<Integer, List<Manga>> mangas;

    public LibraryCategoryAdapter(LibraryFragment fragment, FragmentManager fm) {
        super(fm);
        this.fragment = fragment;
    }

    @Override
    public Fragment getItem(int position) {
        Category category = categories.get(position);
        return LibraryCategoryFragment.newInstance(fragment, category,
                mangas != null ? mangas.get(category.id) : null);
    }

    @Override
    public int getCount() {
        return categories == null ? 0 : categories.size();
    }

    @Override
    public CharSequence getPageTitle(int position) {
        return categories.get(position).name;
    }

    public void setCategories(List<Category> categories) {
        this.categories = categories;
        notifyDataSetChanged();
    }

    public void setMangasOnCategories(Map<Integer, List<Manga>> mangas) {
        this.mangas = mangas;
        for (Map.Entry<Integer, List<Manga>> entry : mangas.entrySet()) {
            LibraryCategoryFragment fragment = getFragment(entry.getKey());
            if (fragment != null) {
                fragment.setMangas(entry.getValue());
            }
        }
    }

    @Nullable
    public LibraryCategoryFragment getFragment(int categoryId) {
        if (categories != null) {
            for (int i = 0; i < categories.size(); i++) {
                if (categories.get(i).id == categoryId) {
                    return (LibraryCategoryFragment) getRegisteredFragment(i);
                }
            }
        }
        return null;
    }

}