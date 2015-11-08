package eu.kanade.mangafeed.presenter;

import android.os.Bundle;

import java.io.File;
import java.util.List;

import javax.inject.Inject;

import de.greenrobot.event.EventBus;
import eu.kanade.mangafeed.data.helpers.DatabaseHelper;
import eu.kanade.mangafeed.data.helpers.DownloadManager;
import eu.kanade.mangafeed.data.helpers.PreferencesHelper;
import eu.kanade.mangafeed.data.models.Chapter;
import eu.kanade.mangafeed.data.models.Manga;
import eu.kanade.mangafeed.data.models.Page;
import eu.kanade.mangafeed.events.SourceMangaChapterEvent;
import eu.kanade.mangafeed.sources.base.Source;
import eu.kanade.mangafeed.ui.activity.ReaderActivity;
import eu.kanade.mangafeed.util.EventBusHook;
import icepick.State;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import timber.log.Timber;

public class ReaderPresenter extends BasePresenter<ReaderActivity> {

    @Inject PreferencesHelper prefs;
    @Inject DatabaseHelper db;
    @Inject DownloadManager downloadManager;

    private Source source;
    private Manga manga;
    private Chapter chapter;
    private List<Page> pageList;
    private boolean isDownloaded;
    @State int currentPage;

    private static final int GET_PAGE_LIST = 1;
    private static final int GET_PAGE_IMAGES = 2;

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        restartableLatestCache(GET_PAGE_LIST,
                () -> getPageListObservable()
                        .doOnNext(pages -> pageList = pages)
                        .doOnCompleted( () -> start(GET_PAGE_IMAGES) ),
                (view, pages) -> {
                    view.onPageListReady(pages);
                    if (currentPage != 0)
                        view.setSelectedPage(currentPage);
                },
                (view, error) -> Timber.e("An error occurred while downloading page list"));

        restartableReplay(GET_PAGE_IMAGES,
                this::getPageImagesObservable,
                (view, page) -> {},
                (view, error) -> Timber.e("An error occurred while downloading an image"));

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
        if (!isDownloaded)
            source.savePageList(chapter.url, pageList);
        saveChapterProgress();
        super.onDestroy();
    }

    @EventBusHook
    public void onEventMainThread(SourceMangaChapterEvent event) {
        source = event.getSource();
        manga = event.getManga();
        chapter = event.getChapter();
        isDownloaded = chapter.downloaded == Chapter.DOWNLOADED;
        if (chapter.last_page_read != 0 && !chapter.read)
            currentPage = chapter.last_page_read;

        start(GET_PAGE_LIST);

        EventBus.getDefault().removeStickyEvent(SourceMangaChapterEvent.class);
    }

    private Observable<List<Page>> getPageListObservable() {
        if (!isDownloaded)
            return source.getCachedPageListOrPullFromNetwork(chapter.url)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread());
        else
            return Observable.just(downloadManager.getSavedPageList(source, manga, chapter));
    }

    private Observable<Page> getPageImagesObservable() {
        Observable<Page> pages;

        if (!isDownloaded) {
            pages = Observable
                    .merge(Observable.from(pageList).filter(page -> page.getImageUrl() != null),
                            source.getRemainingImageUrlsFromPageList(pageList))
                    .flatMap(source::getCachedImage);
        } else {
            File chapterDir = downloadManager.getAbsoluteChapterDirectory(source, manga, chapter);

            pages = Observable.from(pageList)
                    .flatMap(page -> downloadManager.getDownloadedImage(page, source, chapterDir))
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread());
        }
        return pages
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

    public void setCurrentPage(int currentPage) {
        this.currentPage = currentPage;
    }

    private void saveChapterProgress() {
        chapter.last_page_read = currentPage;
        if (currentPage == pageList.size() - 1) {
            chapter.read = true;
        }
        db.insertChapter(chapter).executeAsBlocking();
    }
}
