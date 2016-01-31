package eu.kanade.tachiyomi.ui.reader.viewer.webtoon;

import android.os.Bundle;
import android.support.v7.widget.RecyclerView;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import java.util.List;

import eu.kanade.tachiyomi.data.source.model.Page;
import eu.kanade.tachiyomi.ui.reader.viewer.base.BaseReader;
import eu.kanade.tachiyomi.widget.PreCachingLayoutManager;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.subjects.PublishSubject;

import static android.view.GestureDetector.SimpleOnGestureListener;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

public class WebtoonReader extends BaseReader {

    private WebtoonAdapter adapter;
    private RecyclerView recycler;
    private PreCachingLayoutManager layoutManager;
    private Subscription subscription;
    private Subscription decoderSubscription;
    private GestureDetector gestureDetector;

    private boolean isReady;
    private int scrollDistance;

    private static final String SCROLL_STATE = "scroll_state";

    private static final float LEFT_REGION = 0.33f;
    private static final float RIGHT_REGION = 0.66f;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        adapter = new WebtoonAdapter(this);

        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        scrollDistance = screenHeight * 3 / 4;

        layoutManager = new PreCachingLayoutManager(getActivity());
        layoutManager.setExtraLayoutSpace(screenHeight / 2);
        if (savedState != null) {
            layoutManager.onRestoreInstanceState(savedState.getParcelable(SCROLL_STATE));
        }

        recycler = new RecyclerView(getActivity());
        recycler.setLayoutParams(new ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT));
        recycler.setLayoutManager(layoutManager);
        recycler.setItemAnimator(null);
        recycler.setAdapter(adapter);

        decoderSubscription = getReaderActivity().getPreferences().imageDecoder()
                .asObservable()
                .doOnNext(this::setDecoderClass)
                .skip(1)
                .distinctUntilChanged()
                .subscribe(v -> recycler.setAdapter(adapter));

        gestureDetector = new GestureDetector(recycler.getContext(), new SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                final float positionX = e.getX();

                if (positionX < recycler.getWidth() * LEFT_REGION) {
                    recycler.smoothScrollBy(0, -scrollDistance);
                } else if (positionX > recycler.getWidth() * RIGHT_REGION) {
                    recycler.smoothScrollBy(0, scrollDistance);
                } else {
                    getReaderActivity().onCenterSingleTap();
                }
                return true;
            }
        });

        setPages();
        isReady = true;

        return recycler;
    }

    @Override
    public void onDestroyView() {
        decoderSubscription.unsubscribe();
        super.onDestroyView();
    }

    @Override
    public void onPause() {
        unsubscribeStatus();
        super.onPause();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(SCROLL_STATE, layoutManager.onSaveInstanceState());
    }

    private void unsubscribeStatus() {
        if (subscription != null && !subscription.isUnsubscribed())
            subscription.unsubscribe();
    }

    @Override
    public void setSelectedPage(int pageNumber) {
        recycler.scrollToPosition(getPositionForPage(pageNumber));
    }

    @Override
    public void onPageListReady(List<Page> pages, int currentPage) {
        if (this.pages != pages) {
            this.pages = pages;
            // Restoring current page is not supported. It's getting weird scrolling jumps
            // this.currentPage = currentPage;
            if (isReady) {
                setPages();
            }
        }
    }

    private void setPages() {
        if (pages != null) {
            unsubscribeStatus();
            recycler.clearOnScrollListeners();
            adapter.setPages(pages);
            recycler.setAdapter(adapter);
            updatePageNumber();
            setScrollListener();
            observeStatus(0);
        }
    }

    private void setScrollListener() {
        recycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                currentPage = layoutManager.findLastVisibleItemPosition();
                updatePageNumber();
            }
        });
    }

    @Override
    public boolean onImageTouch(MotionEvent motionEvent) {
        return gestureDetector.onTouchEvent(motionEvent);
    }

    private void observeStatus(int position) {
        if (position == pages.size())
            return;

        final Page page = pages.get(position);

        PublishSubject<Integer> statusSubject = PublishSubject.create();
        page.setStatusSubject(statusSubject);

        // Unsubscribe from the previous page
        unsubscribeStatus();

        subscription = statusSubject
                .startWith(page.getStatus())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(status -> processStatus(position, status));
    }

    private void processStatus(int position, int status) {
        adapter.notifyItemChanged(position);
        if (status == Page.READY) {
            observeStatus(position + 1);
        }
    }

}
