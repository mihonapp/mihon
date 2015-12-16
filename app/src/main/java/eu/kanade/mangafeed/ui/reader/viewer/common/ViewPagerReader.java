package eu.kanade.mangafeed.ui.reader.viewer.common;

import android.support.annotation.CallSuper;
import android.view.MotionEvent;

import java.util.List;

import eu.kanade.mangafeed.data.source.model.Page;
import eu.kanade.mangafeed.ui.reader.ReaderActivity;
import eu.kanade.mangafeed.ui.reader.viewer.base.BaseReader;
import rx.Subscription;

public abstract class ViewPagerReader extends BaseReader {

    protected ViewPagerReaderAdapter adapter;
    protected ViewPagerInterface viewPager;

    protected boolean transitions;
    protected Subscription transitionsSubscription;

    public ViewPagerReader(ReaderActivity activity) {
        super(activity);

        transitionsSubscription = activity.getPreferences().enableTransitions().asObservable()
                .subscribe(value -> transitions = value);
    }

    protected void initializeViewPager() {
        viewPager.setOffscreenPageLimit(2);
        viewPager.setOnChapterBoundariesOutListener(new OnChapterBoundariesOutListener() {
            @Override
            public void onFirstPageOutEvent() {
                onFirstPageOut();
            }

            @Override
            public void onLastPageOutEvent() {
                onLastPageOut();
            }
        });
        viewPager.setOnChapterSingleTapListener(new OnChapterSingleTapListener() {
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
    public boolean onImageTouch(MotionEvent motionEvent) {
        return viewPager.onImageTouch(motionEvent);
    }

    @Override
    public void onPageListReady(List<Page> pages) {
        currentPosition = 0;
        adapter = new ViewPagerReaderAdapter(activity.getSupportFragmentManager(), pages);
        viewPager.setAdapter(adapter);
    }

    @Override
    @CallSuper
    public void destroy() {
        transitionsSubscription.unsubscribe();
    }

    public abstract void onFirstPageOut();
    public abstract void onLastPageOut();

}
