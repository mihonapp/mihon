package eu.kanade.mangafeed.ui.reader.viewer.horizontal;

import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import eu.kanade.mangafeed.data.source.model.Page;
import eu.kanade.mangafeed.ui.reader.ReaderActivity;

public class RightToLeftReader extends HorizontalReader {

    public RightToLeftReader(ReaderActivity activity, FrameLayout container) {
        super(activity, container);
    }

    @Override
    public void onPageListReady(List<Page> pages) {
        ArrayList<Page> inversedPages = new ArrayList<>(pages);
        Collections.reverse(inversedPages);
        adapter.setPages(inversedPages);
        viewPager.setCurrentItem(adapter.getCount() - 1, false);
    }

    @Override
    public int getCurrentPageIndex(int viewerPosition) {
        return getTotalPages() - viewerPosition - 1;
    }

    @Override
    public void onFirstPageOut() {
        // TODO
    }

    @Override
    public void onLastPageOut() {
        // TODO
    }

}
