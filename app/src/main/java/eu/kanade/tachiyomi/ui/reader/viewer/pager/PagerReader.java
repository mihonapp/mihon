package eu.kanade.tachiyomi.ui.reader.viewer.pager;

import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ViewGroup;

import java.util.ArrayList;

import eu.kanade.tachiyomi.R;
import eu.kanade.tachiyomi.data.database.models.Chapter;
import eu.kanade.tachiyomi.data.preference.PreferencesHelper;
import eu.kanade.tachiyomi.data.source.model.Page;
import eu.kanade.tachiyomi.ui.reader.viewer.base.BaseReader;
import eu.kanade.tachiyomi.ui.reader.viewer.pager.horizontal.LeftToRightReader;
import eu.kanade.tachiyomi.ui.reader.viewer.pager.horizontal.RightToLeftReader;
import rx.subscriptions.CompositeSubscription;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

public abstract class PagerReader extends BaseReader {

    protected PagerReaderAdapter adapter;
    protected Pager pager;
    protected GestureDetector gestureDetector;

    protected boolean transitions;
    protected CompositeSubscription subscriptions;

    protected int scaleType = 1;
    protected int zoomStart = 1;

    public static final int ALIGN_AUTO = 1;
    public static final int ALIGN_LEFT = 2;
    public static final int ALIGN_RIGHT = 3;
    public static final int ALIGN_CENTER = 4;

    private static final float LEFT_REGION = 0.33f;
    private static final float RIGHT_REGION = 0.66f;

    protected void initializePager(Pager pager) {
        this.pager = pager;
        pager.setLayoutParams(new ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT));
        pager.setOffscreenPageLimit(1);
        pager.setId(R.id.view_pager);
        pager.setOnChapterBoundariesOutListener(new OnChapterBoundariesOutListener() {
            @Override
            public void onFirstPageOutEvent() {
                getReaderActivity().requestPreviousChapter();
            }

            @Override
            public void onLastPageOutEvent() {
                getReaderActivity().requestNextChapter();
            }
        });
        gestureDetector = createGestureDetector();

        adapter = new PagerReaderAdapter(getChildFragmentManager());
        pager.setAdapter(adapter);

        PreferencesHelper preferences = getReaderActivity().getPreferences();
        subscriptions = new CompositeSubscription();
        subscriptions.add(preferences.imageDecoder()
                .asObservable()
                .doOnNext(this::setDecoderClass)
                .skip(1)
                .distinctUntilChanged()
                .subscribe(v -> pager.setAdapter(adapter)));

        subscriptions.add(preferences.imageScaleType()
                .asObservable()
                .doOnNext(this::setImageScaleType)
                .skip(1)
                .distinctUntilChanged()
                .subscribe(v -> pager.setAdapter(adapter)));

        subscriptions.add(preferences.zoomStart()
                .asObservable()
                .doOnNext(this::setZoomStart)
                .skip(1)
                .distinctUntilChanged()
                .subscribe(v -> pager.setAdapter(adapter)));

        subscriptions.add(preferences.enableTransitions()
                .asObservable()
                .subscribe(value -> transitions = value));

        setPages();
    }

    @Override
    public void onDestroyView() {
        subscriptions.unsubscribe();
        super.onDestroyView();
    }

    protected GestureDetector createGestureDetector() {
        return new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                final float positionX = e.getX();

                if (positionX < pager.getWidth() * LEFT_REGION) {
                    onLeftSideTap();
                } else if (positionX > pager.getWidth() * RIGHT_REGION) {
                    onRightSideTap();
                } else {
                    getReaderActivity().onCenterSingleTap();
                }
                return true;
            }
        });
    }

    @Override
    public void onSetChapter(Chapter chapter, Page currentPage) {
        pages = new ArrayList<>(chapter.getPages());
        this.currentPage = getPageIndex(currentPage); // we might have a new page object

        // This method can be called before the view is created
        if (pager != null) {
            setPages();
        }
    }

    public void onAppendChapter(Chapter chapter) {
        pages.addAll(chapter.getPages());

        // This method can be called before the view is created
        if (pager != null) {
            adapter.setPages(pages);
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
        pager.setCurrentItem(pageNumber, false);
    }

    protected void onLeftSideTap() {
        moveToPrevious();
    }

    protected void onRightSideTap() {
        moveToNext();
    }

    public void moveToNext() {
        if (pager.getCurrentItem() != pager.getAdapter().getCount() - 1) {
            pager.setCurrentItem(pager.getCurrentItem() + 1, transitions);
        } else {
            getReaderActivity().requestNextChapter();
        }
    }

    public void moveToPrevious() {
        if (pager.getCurrentItem() != 0) {
            pager.setCurrentItem(pager.getCurrentItem() - 1, transitions);
        } else {
            getReaderActivity().requestPreviousChapter();
        }
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

}
