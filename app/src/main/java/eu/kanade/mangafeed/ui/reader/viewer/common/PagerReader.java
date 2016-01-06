package eu.kanade.mangafeed.ui.reader.viewer.common;

import android.view.MotionEvent;
import android.view.ViewGroup;

import java.util.List;

import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.source.model.Page;
import eu.kanade.mangafeed.ui.reader.viewer.base.BaseReader;
import rx.Subscription;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

public abstract class PagerReader extends BaseReader {

    protected PagerReaderAdapter adapter;
    protected Pager pager;

    protected boolean transitions;
    protected Subscription transitionsSubscription;

    protected void initializePager(Pager pager) {
        this.pager = pager;
        pager.setLayoutParams(new ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT));
        pager.setOffscreenPageLimit(2);
        pager.setId(R.id.view_pager);
        pager.setOnChapterBoundariesOutListener(new OnChapterBoundariesOutListener() {
            @Override
            public void onFirstPageOutEvent() {
                onFirstPageOut();
            }

            @Override
            public void onLastPageOutEvent() {
                onLastPageOut();
            }
        });
        pager.setOnChapterSingleTapListener(new OnChapterSingleTapListener() {
            @Override
            public void onCenterTap() {
                getReaderActivity().onCenterSingleTap();
            }

            @Override
            public void onLeftSideTap() {
                pager.setCurrentItem(pager.getCurrentItem() - 1, transitions);
            }

            @Override
            public void onRightSideTap() {
                pager.setCurrentItem(pager.getCurrentItem() + 1, transitions);
            }
        });

        adapter = new PagerReaderAdapter(getChildFragmentManager());
        pager.setAdapter(adapter);
        setPages();

        transitionsSubscription = getReaderActivity().getPreferences().enableTransitions()
                .asObservable()
                .subscribe(value -> transitions = value);
    }

    @Override
    public void onDestroyView() {
        transitionsSubscription.unsubscribe();
        super.onDestroyView();
    }

    @Override
    public void onPageListReady(List<Page> pages, int currentPage) {
        if (this.pages != pages) {
            this.pages = pages;
            this.currentPage = currentPage;
            if (isResumed()) {
                setPages();
            }
        }
    }

    protected void setPages() {
        if (pages != null) {
            pager.clearOnPageChangeListeners();
            adapter.setPages(pages);
            setSelectedPage(currentPage);
            updatePageNumber();
            pager.setOnPageChangeListener(this::onPageChanged);
        }
    }

    @Override
    public void setSelectedPage(int pageNumber) {
        pager.setCurrentItem(getPositionForPage(pageNumber), false);
    }

    @Override
    public boolean onImageTouch(MotionEvent motionEvent) {
        return pager.onImageTouch(motionEvent);
    }

    public abstract void onFirstPageOut();
    public abstract void onLastPageOut();

}
