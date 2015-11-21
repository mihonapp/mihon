package eu.kanade.mangafeed.ui.reader.viewer.webtoon;

import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.MotionEvent;

import java.util.List;

import eu.kanade.mangafeed.data.source.model.Page;
import eu.kanade.mangafeed.ui.reader.ReaderActivity;
import eu.kanade.mangafeed.ui.reader.viewer.base.BaseReader;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.subjects.PublishSubject;

public class WebtoonReader extends BaseReader {

    private RecyclerView recycler;
    private LinearLayoutManager layoutManager;
    private WebtoonAdapter adapter;
    private List<Page> pages;
    private Subscription subscription;

    public WebtoonReader(ReaderActivity activity) {
        super(activity);

        recycler = new RecyclerView(activity);
        layoutManager = new LinearLayoutManager(activity);
        recycler.setLayoutManager(layoutManager);
        adapter = new WebtoonAdapter(activity);
        recycler.setAdapter(adapter);

        recycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                currentPosition = layoutManager.findFirstVisibleItemPosition();
                updatePageNumber();
            }
        });

        container.addView(recycler);
    }

    @Override
    public int getTotalPages() {
        return pages.size();
    }

    @Override
    public void setSelectedPage(int pageNumber) {
        // TODO
        return;
    }

    @Override
    public void onPageListReady(List<Page> pages) {
        this.pages = pages;
        observeStatus(0);
    }

    @Override
    public boolean onImageTouch(MotionEvent motionEvent) {
        return true;
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

    @Override
    public void destroy() {
        if (subscription != null && !subscription.isUnsubscribed())
            subscription.unsubscribe();
    }
}
