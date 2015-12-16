package eu.kanade.mangafeed.ui.reader.viewer.vertical;

import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.ui.reader.ReaderActivity;
import eu.kanade.mangafeed.ui.reader.viewer.common.ViewPagerInterface;
import eu.kanade.mangafeed.ui.reader.viewer.common.ViewPagerReader;

public class VerticalReader extends ViewPagerReader {

    public VerticalReader(ReaderActivity activity) {
        super(activity);
        activity.getLayoutInflater().inflate(R.layout.reader_vertical, container);

        viewPager = (ViewPagerInterface) container.findViewById(R.id.view_pager);
        initializeViewPager();
        ((VerticalViewPager) viewPager).addOnPageChangeListener(new PageChangeListener());
    }

    @Override
    public void onFirstPageOut() {
        requestPreviousChapter();
    }

    @Override
    public void onLastPageOut() {
        requestNextChapter();
    }

    private class PageChangeListener extends VerticalViewPagerImpl.SimpleOnPageChangeListener {
        @Override
        public void onPageSelected(int position) {
            onPageChanged(position);
        }
    }

}
