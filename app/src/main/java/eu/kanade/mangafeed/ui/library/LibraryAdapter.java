package eu.kanade.mangafeed.ui.library;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;

import java.util.List;

import eu.kanade.mangafeed.data.database.models.Category;

class LibraryAdapter extends FragmentStatePagerAdapter {

    private List<Category> categories;

    public LibraryAdapter(FragmentManager fm) {
        super(fm);
    }

    @Override
    public Fragment getItem(int position) {
        Category category = categories.get(position);
        return LibraryCategoryFragment.newInstance(category);
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

}