package eu.kanade.mangafeed.ui.reader.viewer.common;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;

import java.util.List;

import eu.kanade.mangafeed.data.source.model.Page;
import eu.kanade.mangafeed.ui.base.adapter.SmartFragmentStatePagerAdapter;

public class ViewPagerReaderAdapter extends SmartFragmentStatePagerAdapter {

    private List<Page> pages;

    public ViewPagerReaderAdapter(FragmentManager fragmentManager, List<Page> pages) {
        super(fragmentManager);
        this.pages = pages;
    }

    @Override
    public int getCount() {
        return pages.size();
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
