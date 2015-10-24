package eu.kanade.mangafeed.ui.viewer;

import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import eu.kanade.mangafeed.data.models.Page;
import eu.kanade.mangafeed.ui.activity.ReaderActivity;
import eu.kanade.mangafeed.ui.viewer.base.ViewPagerViewer;

public class RightToLeftViewer extends ViewPagerViewer {

    public RightToLeftViewer(ReaderActivity activity, FrameLayout container) {
        super(activity, container);
    }

    @Override
    public void onPageListReady(List<Page> pages) {
        ArrayList<Page> inversedPages = new ArrayList<>(pages);
        Collections.reverse(inversedPages);
        adapter.setPages(inversedPages);
        viewPager.setCurrentItem(adapter.getCount()-1);
    }

    @Override
    public int getCurrentPageFromPos(int position) {
        return getTotalPages() - position;
    }

    @Override
    public int getPosFromPage(Page page) {
        return getTotalPages() - (page.getPageNumber() + 1);
    }

    @Override
    public void onFirstPageOut() {

    }

    @Override
    public void onLastPageOut() {

    }

}
