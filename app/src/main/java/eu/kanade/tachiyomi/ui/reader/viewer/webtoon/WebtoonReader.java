package eu.kanade.tachiyomi.ui.reader.viewer.webtoon;

import android.os.Bundle;
import android.support.annotation.Nullable;
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

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        adapter = new WebtoonAdapter(this);
        layoutManager = new PreCachingLayoutManager(getActivity());
        layoutManager.setExtraLayoutSpace(getResources().getDisplayMetrics().heightPixels);

        recycler = new RecyclerView(getActivity());
        recycler.setLayoutParams(new ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT));
        recycler.setLayoutManager(layoutManager);
        recycler.setItemAnimator(null);
        recycler.setAdapter(adapter);

        decoderSubscription = getReaderActivity().getPreferences().imageDecoder()
                .asObservable()
                .doOnNext(this::setRegionDecoderClass)
                .skip(1)
                .distinctUntilChanged()
                .subscribe(v -> adapter.notifyDataSetChanged());

        gestureDetector = new GestureDetector(getActivity(), new SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                getReaderActivity().onCenterSingleTap();
                return true;
            }

            @Override
            public boolean onDown(MotionEvent e) {
                // The only way I've found to allow panning. Double tap event (zoom) is lost
                // but panning should be the most used one
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
            if (isResumed()) {
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
