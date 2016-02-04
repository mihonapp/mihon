package eu.kanade.tachiyomi.ui.reader.viewer.pager;

import android.view.MotionEvent;
import android.view.ViewGroup;

import java.util.List;

import eu.kanade.tachiyomi.R;
import eu.kanade.tachiyomi.data.preference.PreferencesHelper;
import eu.kanade.tachiyomi.data.source.model.Page;
import eu.kanade.tachiyomi.ui.reader.viewer.base.BaseReader;
import eu.kanade.tachiyomi.ui.reader.viewer.base.OnChapterBoundariesOutListener;
import eu.kanade.tachiyomi.ui.reader.viewer.base.OnChapterSingleTapListener;
import eu.kanade.tachiyomi.ui.reader.viewer.pager.horizontal.LeftToRightReader;
import eu.kanade.tachiyomi.ui.reader.viewer.pager.horizontal.RightToLeftReader;
import rx.subscriptions.CompositeSubscription;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

public abstract class PagerReader extends BaseReader {

    protected PagerReaderAdapter adapter;
    protected Pager pager;

    private boolean isReady;
    protected boolean transitions;
    protected CompositeSubscription subscriptions;

    protected int scaleType = 1;
    protected int zoomStart = 1;

    public static final int ALIGN_AUTO = 1;
    public static final int ALIGN_LEFT = 2;
    public static final int ALIGN_RIGHT = 3;
    public static final int ALIGN_CENTER = 4;

    protected void initializePager(Pager pager) {
        this.pager = pager;
        pager.setLayoutParams(new ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT));
        pager.setOffscreenPageLimit(1);
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

        PreferencesHelper preferences = getReaderActivity().getPreferences();
        subscriptions = new CompositeSubscription();
        subscriptions.add(preferences.imageDecoder()
                .asObservable()
                .doOnNext(this::setDecoderClass)
                .skip(1)
                .distinctUntilChanged()
                .subscribe(v -> adapter.notifyDataSetChanged()));

        subscriptions.add(preferences.imageScaleType()
                .asObservable()
                .doOnNext(this::setImageScaleType)
                .skip(1)
                .distinctUntilChanged()
                .subscribe(v -> adapter.notifyDataSetChanged()));

        subscriptions.add(preferences.zoomStart()
                .asObservable()
                .doOnNext(this::setZoomStart)
                .skip(1)
                .distinctUntilChanged()
                .subscribe(v -> adapter.notifyDataSetChanged()));

        subscriptions.add(preferences.enableTransitions()
                .asObservable()
                .subscribe(value -> transitions = value));

        setPages();
        isReady = true;
    }

    @Override
    public void onDestroyView() {
        subscriptions.unsubscribe();
        super.onDestroyView();
    }

    @Override
    public void onPageListReady(List<Page> pages, int currentPage) {
        if (this.pages != pages) {
            this.pages = pages;
            this.currentPage = currentPage;
            if (isReady) {
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

    private void setImageScaleType(int scaleType) {
        this.scaleType = scaleType;
    }

    private void setZoomStart(int zoomStart) {
        if (zoomStart == ALIGN_AUTO) {
            if (this instanceof LeftToRightReader)
                setZoomStart(ALIGN_LEFT);
            else if (this instanceof RightToLeftReader)
                setZoomStart(ALIGN_RIGHT);
            else
                setZoomStart(ALIGN_CENTER);
        } else {
            this.zoomStart = zoomStart;
        }
    }

    public abstract void onFirstPageOut();
    public abstract void onLastPageOut();

}
