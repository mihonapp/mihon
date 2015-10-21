package eu.kanade.mangafeed.presenter;

import android.os.Bundle;

import java.util.List;

import de.greenrobot.event.EventBus;
import eu.kanade.mangafeed.data.models.Chapter;
import eu.kanade.mangafeed.data.models.Page;
import eu.kanade.mangafeed.sources.Source;
import eu.kanade.mangafeed.ui.activity.ReaderActivity;
import eu.kanade.mangafeed.util.EventBusHook;
import eu.kanade.mangafeed.util.events.SourceChapterEvent;
import icepick.State;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

public class ReaderPresenter extends BasePresenter<ReaderActivity> {

    private Source source;
    private Chapter chapter;
    private List<Page> pageList;
    @State int savedSelectedPage = -1;

    private static final int GET_PAGE_LIST = 1;
    private static final int GET_PAGE_IMAGES = 2;

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        restartableLatestCache(GET_PAGE_LIST,
                () -> getPageListObservable()
                        .doOnNext(pages -> pageList = pages)
                        .doOnCompleted(() -> start(GET_PAGE_IMAGES)),
                (view, pages) -> {
                    view.onPageList(pages);
                });

        restartableReplay(GET_PAGE_IMAGES,
                this::getPageImagesObservable,
                (view, page) -> {
                    view.onPageDownloaded(page);
                    if (page.getPageNumber() == savedSelectedPage) {
                        view.setCurrentPage(savedSelectedPage);
                    }
                });
    }

    @Override
    protected void onTakeView(ReaderActivity view) {
        super.onTakeView(view);
        registerForStickyEvents();
    }

    @Override
    protected void onDropView() {
        unregisterForEvents();
        super.onDropView();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().removeStickyEvent(SourceChapterEvent.class);
        source.savePageList(chapter.url, pageList);
    }

    @EventBusHook
    public void onEventMainThread(SourceChapterEvent event) {
        if (source == null || chapter == null) {
            source = event.getSource();
            chapter = event.getChapter();

            start(1);
        }
    }

    private Observable<List<Page>> getPageListObservable() {
        return source.pullPageListFromNetwork(chapter.url)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

    private Observable<Page> getPageImagesObservable() {
        return Observable.merge(
                Observable.from(pageList).filter(page -> page.getImageUrl() != null),
                source.getRemainingImageUrlsFromPageList(pageList)
                        .doOnNext(this::replacePageUrl))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

    private void replacePageUrl(Page page) {
        for (int i = 0; i < pageList.size(); i++) {
            if (pageList.get(i).getPageNumber() == page.getPageNumber()) {
                pageList.set(i, page);
                return;
            }
        }
    }

    public void setCurrentPage(int savedPage) {
        this.savedSelectedPage = savedPage;
    }
}
