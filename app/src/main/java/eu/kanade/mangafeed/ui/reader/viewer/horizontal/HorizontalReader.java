package eu.kanade.mangafeed.ui.reader.viewer.horizontal;

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

public abstract class HorizontalReader extends BaseReader {

    @Bind(R.id.view_pager) HorizontalViewPager viewPager;

    protected ViewPagerReaderAdapter adapter;

    private boolean transitions;
    private Subscription transitionsSubscription;

    public HorizontalReader(ReaderActivity activity) {
        super(activity);
        activity.getLayoutInflater().inflate(R.layout.reader_horizontal, container);
        ButterKnife.bind(this, container);

        transitionsSubscription = activity.getPreferences().enableTransitions().asObservable()
                .subscribe(value -> transitions = value);

        viewPager.setOffscreenPageLimit(2);
        viewPager.addOnPageChangeListener(new HorizontalViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                currentPosition = position;
                updatePageNumber();
            }
        });
        viewPager.setOnChapterBoundariesOutListener(new HorizontalViewPager.OnChapterBoundariesOutListener() {
            @Override
            public void onFirstPageOutEvent() {
                onFirstPageOut();
            }

            @Override
            public void onLastPageOutEvent() {
                onLastPageOut();
            }
        });
        viewPager.setOnChapterSingleTapListener(new HorizontalViewPager.OnChapterSingleTapListener() {
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

    public abstract void onFirstPageOut();
    public abstract void onLastPageOut();

}
