package eu.kanade.tachiyomi.ui.reader.viewer.pager;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.view.ViewGroup;

import java.util.List;

import eu.kanade.tachiyomi.data.source.model.Page;

public class PagerReaderAdapter extends FragmentStatePagerAdapter {

    private List<Page> pages;

    public PagerReaderAdapter(FragmentManager fragmentManager) {
        super(fragmentManager);
    }

    @Override
    public int getCount() {
        return pages == null ? 0 : pages.size();
    }

    @Override
    public Fragment getItem(int position) {
        return PagerReaderFragment.newInstance();
    }

    @Override
    public Object instantiateItem(ViewGroup container, int position) {
        PagerReaderFragment f = (PagerReaderFragment) super.instantiateItem(container, position);
        f.setPage(pages.get(position));
        return f;
    }

    @Override
    public int getItemPosition(Object object) {
        return POSITION_NONE;
    }

    public List<Page> getPages() {
        return pages;
    }

    public void setPages(List<Page> pages) {
        this.pages = pages;
        notifyDataSetChanged();
    }

}
