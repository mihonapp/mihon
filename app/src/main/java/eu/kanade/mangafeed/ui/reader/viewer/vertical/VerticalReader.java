package eu.kanade.mangafeed.ui.reader.viewer.vertical;

import android.support.v4.view.ViewPager;
import android.view.MotionEvent;
import android.widget.FrameLayout;

import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.source.model.Page;
import eu.kanade.mangafeed.ui.reader.ReaderActivity;
import eu.kanade.mangafeed.ui.reader.viewer.base.BaseReader;
import eu.kanade.mangafeed.ui.reader.viewer.common.ViewPagerReaderAdapter;

public class VerticalReader extends BaseReader {

    @Bind(R.id.view_pager) VerticalViewPager viewPager;

    private ViewPagerReaderAdapter adapter;

    public VerticalReader(ReaderActivity activity, FrameLayout container) {
        super(activity, container);
        activity.getLayoutInflater().inflate(R.layout.reader_vertical, container);
        ButterKnife.bind(this, container);

        viewPager.setOffscreenPageLimit(3);
        viewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                currentPosition = position;
                updatePageNumber();
            }
        });
    }

    @Override
    public int getTotalPages() {
        return adapter.getCount();
    }

    @Override
    public void setSelectedPage(int pageNumber) {
        viewPager.setCurrentItem(getCurrentPageIndex(pageNumber));
    }

    @Override
    public void onPageListReady(List<Page> pages) {
        currentPosition = 0;
        adapter = new ViewPagerReaderAdapter(activity.getSupportFragmentManager(), pages);
        viewPager.setAdapter(adapter);
    }

    @Override
    public boolean onImageTouch(MotionEvent motionEvent) {
        return true;
    }
}
