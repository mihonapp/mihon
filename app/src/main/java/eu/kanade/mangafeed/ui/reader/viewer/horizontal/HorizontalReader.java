package eu.kanade.mangafeed.ui.reader.viewer.horizontal;

import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.ui.reader.ReaderActivity;
import eu.kanade.mangafeed.ui.reader.viewer.common.ViewPagerInterface;
import eu.kanade.mangafeed.ui.reader.viewer.common.ViewPagerReader;

public abstract class HorizontalReader extends ViewPagerReader {

    public HorizontalReader(ReaderActivity activity) {
        super(activity);
        activity.getLayoutInflater().inflate(R.layout.reader_horizontal, container);

        viewPager = (ViewPagerInterface) container.findViewById(R.id.view_pager);
        initializeViewPager();
        ((HorizontalViewPager) viewPager).addOnPageChangeListener(new PageChangeListener());
    }

    private class PageChangeListener extends HorizontalViewPager.SimpleOnPageChangeListener {
        @Override
        public void onPageSelected(int position) {
            onPageChanged(position);
        }
    }

}
