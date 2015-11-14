package eu.kanade.mangafeed.ui.reader.viewer.common;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;

import java.util.List;

import eu.kanade.mangafeed.data.source.model.Page;

public class ViewPagerReaderAdapter extends SmartFragmentStatePagerAdapter {

    private List<Page> pages;

    public ViewPagerReaderAdapter(FragmentManager fragmentManager) {
        super(fragmentManager);
    }

    @Override
    public int getCount() {
        if (pages != null)
            return pages.size();

        return 0;
    }

    @Override
    public Fragment getItem(int position) {
        return ViewPagerReaderFragment.newInstance(pages.get(position));
    }

    public List<Page> getPages() {
        return pages;
    }

    public void setPages(List<Page> pages) {
        this.pages = pages;
        notifyDataSetChanged();
    }

}
