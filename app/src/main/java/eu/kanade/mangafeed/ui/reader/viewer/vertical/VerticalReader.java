package eu.kanade.mangafeed.ui.reader.viewer.vertical;

import android.support.v4.view.ViewPager;
import android.view.MotionEvent;

import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.source.model.Page;
import eu.kanade.mangafeed.ui.reader.ReaderActivity;
import eu.kanade.mangafeed.ui.reader.viewer.base.BaseReader;
import eu.kanade.mangafeed.ui.reader.viewer.common.ViewPagerReaderAdapter;
import rx.Subscription;

public class VerticalReader extends BaseReader {

    @Bind(R.id.view_pager) VerticalViewPager viewPager;

    private ViewPagerReaderAdapter adapter;

    private boolean transitions;
    private Subscription transitionsSubscription;

    public VerticalReader(ReaderActivity activity) {
        super(activity);
        activity.getLayoutInflater().inflate(R.layout.reader_vertical, container);
        ButterKnife.bind(this, container);

        transitionsSubscription = activity.getPreferences().enableTransitions().asObservable()
                .subscribe(value -> transitions = value);

        viewPager.setOffscreenPageLimit(2);
        viewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                currentPosition = position;
                updatePageNumber();
            }
        });
        viewPager.setOnChapterBoundariesOutListener(new VerticalViewPager.OnChapterBoundariesOutListener() {
            @Override
            public void onFirstPageOutEvent() {
                requestPreviousChapter();
            }

            @Override
            public void onLastPageOutEvent() {
                requestNextChapter();
            }
        });
        viewPager.setOnChapterSingleTapListener(new VerticalViewPager.OnChapterSingleTapListener() {
            @Override
            public void onCenterTap() {
                activity.onCenterSingleTap();
            }

            @Override
            public void onLeftSideTap() {
                viewPager.setCurrentItem(viewPager.getCurrentItem() - 1, transitions);
            }

            @Override
            public void onRightSideTap() {
                viewPager.setCurrentItem(viewPager.getCurrentItem() + 1, transitions);
            }
        });
    }

    @Override
    public int getTotalPages() {
        return adapter.getCount();
    }

    @Override
    public void setSelectedPage(int pageNumber) {
        viewPager.setCurrentItem(getCurrentPageIndex(pageNumber), false);
    }

    @Override
    public void onPageListReady(List<Page> pages) {
        currentPosition = 0;
        adapter = new ViewPagerReaderAdapter(activity.getSupportFragmentManager(), pages);
        viewPager.setAdapter(adapter);
    }

    @Override
    public boolean onImageTouch(MotionEvent motionEvent) {
        return viewPager.onImageTouch(motionEvent);
    }

    @Override
    public void destroy() {
        transitionsSubscription.unsubscribe();
    }
}
