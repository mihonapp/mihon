package eu.kanade.tachiyomi.ui.reader.viewer.webtoon;

import android.os.Bundle;
import android.support.v7.widget.RecyclerView;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;

import eu.kanade.tachiyomi.data.database.models.Chapter;
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
    protected GestureDetector gestureDetector;

    private int scrollDistance;

    private static final String SAVED_POSITION = "saved_position";

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
            layoutManager.scrollToPositionWithOffset(savedState.getInt(SAVED_POSITION), 0);
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
                    moveToPrevious();
                } else if (positionX > recycler.getWidth() * RIGHT_REGION) {
                    moveToNext();
                } else {
                    getReaderActivity().onCenterSingleTap();
                }
                return true;
            }
        });

        setPages();
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
        int savedPosition = pages != null ?
                pages.get(layoutManager.findFirstVisibleItemPosition()).getPageNumber() : 0;
        outState.putInt(SAVED_POSITION, savedPosition);
    }

    private void unsubscribeStatus() {
        if (subscription != null && !subscription.isUnsubscribed())
            subscription.unsubscribe();
    }

    @Override
    public void setSelectedPage(int pageNumber) {
        recycler.scrollToPosition(pageNumber);
    }

    @Override
    public void moveToNext() {
        recycler.smoothScrollBy(0, scrollDistance);
    }

    @Override
    public void moveToPrevious() {
        recycler.smoothScrollBy(0, -scrollDistance);
    }

    @Override
    public void onSetChapter(Chapter chapter, Page currentPage) {
        pages = new ArrayList<>(chapter.getPages());
        // Restoring current page is not supported. It's getting weird scrolling jumps
        // this.currentPage = currentPage;

        // This method can be called before the view is created
        if (recycler != null) {
            setPages();
        }
    }

    @Override
    public void onAppendChapter(Chapter chapter) {
        int insertStart = pages.size();
        pages.addAll(chapter.getPages());

        // This method can be called before the view is created
        if (recycler != null) {
            adapter.setPages(pages);
            adapter.notifyItemRangeInserted(insertStart, chapter.getPages().size());
            if (subscription != null && subscription.isUnsubscribed()) {
                observeStatus(insertStart);
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
                int page = layoutManager.findLastVisibleItemPosition();
                if (page != currentPage) {
                    onPageChanged(page);
                }
            }
        });
    }

    private void observeStatus(int position) {
        if (position == pages.size()) {
            unsubscribeStatus();
            return;
        }

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
