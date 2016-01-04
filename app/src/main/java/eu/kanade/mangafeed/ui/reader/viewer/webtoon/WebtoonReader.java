package eu.kanade.mangafeed.ui.reader.viewer.webtoon;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import java.util.List;

import eu.kanade.mangafeed.data.source.model.Page;
import eu.kanade.mangafeed.ui.reader.viewer.base.BaseReader;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.subjects.PublishSubject;

import static android.view.GestureDetector.SimpleOnGestureListener;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

public class WebtoonReader extends BaseReader {

    private WebtoonAdapter adapter;
    private Subscription subscription;
    private GestureDetector gestureDetector;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        adapter = new WebtoonAdapter(this);
        LinearLayoutManager layoutManager = new LinearLayoutManager(getActivity());

        RecyclerView recycler = new RecyclerView(getActivity());
        recycler.setLayoutParams(new ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT));
        recycler.setLayoutManager(layoutManager);
        recycler.setItemAnimator(null);
        recycler.setAdapter(adapter);
        recycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                currentPage = layoutManager.findLastVisibleItemPosition();
                updatePageNumber();
            }
        });

        gestureDetector = new GestureDetector(getActivity(), new SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                getReaderActivity().onCenterSingleTap();
                return true;
            }
        });

        setPages();

        return recycler;
    }

    @Override
    public void onPause() {
        if (subscription != null && !subscription.isUnsubscribed())
            subscription.unsubscribe();
        super.onPause();
    }

    @Override
    public void setSelectedPage(int pageNumber) {
        // TODO
        return;
    }

    @Override
    public void onPageListReady(List<Page> pages, int currentPage) {
        this.pages = pages;
        if (isResumed()) {
            setPages();
        }
    }

    private void setPages() {
        if (pages != null) {
            observeStatus(0);
        }
    }

    @Override
    public boolean onImageTouch(MotionEvent motionEvent) {
        return gestureDetector.onTouchEvent(motionEvent);
    }

    private void observeStatus(int position) {
        if (position == pages.size())
            return;

        final Page page = pages.get(position);
        adapter.addPage(page);

        PublishSubject<Integer> statusSubject = PublishSubject.create();
        page.setStatusSubject(statusSubject);

        if (subscription != null && !subscription.isUnsubscribed())
            subscription.unsubscribe();

        subscription = statusSubject
                .startWith(page.getStatus())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(status -> processStatus(position, status));
    }

    private void processStatus(int position, int status) {
        switch (status) {
            case Page.LOAD_PAGE:
                break;
            case Page.DOWNLOAD_IMAGE:
                break;
            case Page.READY:
                adapter.notifyItemChanged(position);
                observeStatus(position + 1);
                break;
            case Page.ERROR:
                break;
        }
    }

}
