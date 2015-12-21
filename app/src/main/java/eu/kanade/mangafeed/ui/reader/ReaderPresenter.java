package eu.kanade.mangafeed.ui.reader;

import android.os.Bundle;
import android.support.annotation.NonNull;

import java.io.File;
import java.util.List;

import javax.inject.Inject;

import de.greenrobot.event.EventBus;
import eu.kanade.mangafeed.data.database.DatabaseHelper;
import eu.kanade.mangafeed.data.database.models.Chapter;
import eu.kanade.mangafeed.data.database.models.Manga;
import eu.kanade.mangafeed.data.download.DownloadManager;
import eu.kanade.mangafeed.data.mangasync.MangaSyncManager;
import eu.kanade.mangafeed.data.mangasync.base.MangaSyncService;
import eu.kanade.mangafeed.data.preference.PreferencesHelper;
import eu.kanade.mangafeed.data.source.SourceManager;
import eu.kanade.mangafeed.data.source.base.Source;
import eu.kanade.mangafeed.data.source.model.Page;
import eu.kanade.mangafeed.data.sync.UpdateMangaSyncService;
import eu.kanade.mangafeed.event.ReaderEvent;
import eu.kanade.mangafeed.ui.base.presenter.BasePresenter;
import eu.kanade.mangafeed.util.EventBusHook;
import icepick.State;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;
import timber.log.Timber;

public class ReaderPresenter extends BasePresenter<ReaderActivity> {

    @Inject PreferencesHelper prefs;
    @Inject DatabaseHelper db;
    @Inject DownloadManager downloadManager;
    @Inject MangaSyncManager syncManager;
    @Inject SourceManager sourceManager;

    @State Manga manga;
    @State Chapter chapter;
    @State int sourceId;
    @State boolean isDownloaded;
    @State int currentPage;
    private Source source;
    private Chapter nextChapter;
    private Chapter previousChapter;
    private List<Page> pageList;
    private List<Page> nextChapterPageList;

    private PublishSubject<Page> retryPageSubject;

    private static final int GET_PAGE_LIST = 1;
    private static final int GET_PAGE_IMAGES = 2;
    private static final int RETRY_IMAGES = 3;
    private static final int PRELOAD_NEXT_CHAPTER = 4;

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        if (savedState != null) {
            onProcessRestart();
        }

        retryPageSubject = PublishSubject.create();

        restartableLatestCache(PRELOAD_NEXT_CHAPTER,
                this::getPreloadNextChapterObservable,
                (view, pages) -> {},
                (view, error) -> Timber.e("An error occurred while preloading a chapter"));

        restartableReplay(GET_PAGE_IMAGES,
                () -> getPageImagesObservable()
                        .doOnCompleted(this::preloadNextChapter),
                (view, page) -> {},
                (view, error) -> Timber.e("An error occurred while downloading an image"));

        restartableLatestCache(RETRY_IMAGES,
                this::getRetryPageObservable,
                (view, page) -> {},
                (view, error) -> Timber.e("An error occurred while downloading an image"));

        restartableLatestCache(GET_PAGE_LIST,
                () -> getPageListObservable()
                        .doOnNext(pages -> pageList = pages)
                        .doOnCompleted(() -> {
                            getAdjacentChapters();
                            start(GET_PAGE_IMAGES);
                            start(RETRY_IMAGES);
                        }),
                (view, pages) -> {
                    view.onChapterReady(pages, manga, chapter);
                    if (currentPage != 0)
                        view.setSelectedPage(currentPage);
                },
                (view, error) -> view.onChapterError());

        registerForStickyEvents();
    }

    @Override
    protected void onDestroy() {
        unregisterForEvents();
        onChapterLeft();
        updateMangaSyncLastChapterRead();
        super.onDestroy();
    }

    @Override
    protected void onSave(@NonNull Bundle state) {
        if (pageList != null && !isDownloaded)
            source.savePageList(chapter.url, pageList);

        super.onSave(state);
    }

    private void onProcessRestart() {
        source = sourceManager.get(sourceId);

        // These are started by GET_PAGE_LIST, so we don't let them restart itselves
        stop(GET_PAGE_IMAGES);
        stop(RETRY_IMAGES);
        stop(PRELOAD_NEXT_CHAPTER);
    }

    @EventBusHook
    public void onEventMainThread(ReaderEvent event) {
        EventBus.getDefault().removeStickyEvent(event);
        manga = event.getManga();
        source = event.getSource();
        sourceId = source.getId();
        loadChapter(event.getChapter());
    }

    // Returns the page list of a chapter
    private Observable<List<Page>> getPageListObservable() {
        return isDownloaded ?
                // Fetch the page list from disk
                Observable.just(downloadManager.getSavedPageList(source, manga, chapter)) :
                // Fetch the page list from cache or fallback to network
                source.getCachedPageListOrPullFromNetwork(chapter.url)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread());
    }

    // Get the chapter images from network or disk
    private Observable<Page> getPageImagesObservable() {
        Observable<Page> pageObservable;

        if (!isDownloaded) {
            pageObservable = source.getAllImageUrlsFromPageList(pageList)
                    .flatMap(source::getCachedImage, 3);
        } else {
            File chapterDir = downloadManager.getAbsoluteChapterDirectory(source, manga, chapter);
            pageObservable = Observable.from(pageList)
                    .flatMap(page -> downloadManager.getDownloadedImage(page, chapterDir));
        }
        return pageObservable
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

    // Listen for retry page events
    private Observable<Page> getRetryPageObservable() {
        return retryPageSubject
                .observeOn(Schedulers.io())
                .flatMap(page -> page.getImageUrl() == null ?
                        source.getImageUrlFromPage(page) :
                        Observable.just(page))
                .flatMap(source::getCachedImage)
                .observeOn(AndroidSchedulers.mainThread());
    }

    // Preload the first pages of the next chapter
    private Observable<Page> getPreloadNextChapterObservable() {
        return source.getCachedPageListOrPullFromNetwork(nextChapter.url)
                .flatMap(pages -> {
                    nextChapterPageList = pages;
                    // Preload at most 5 pages
                    int pagesToPreload = Math.min(pages.size(), 5);
                    return Observable.from(pages).take(pagesToPreload);
                })
                .concatMap(page -> page.getImageUrl() == null ?
                        source.getImageUrlFromPage(page) :
                        Observable.just(page))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnCompleted(this::stopPreloadingNextChapter);
    }

    // Loads the given chapter
    private void loadChapter(Chapter chapter) {
        // Before loading the chapter, stop preloading (if it's working) and save current progress
        stopPreloadingNextChapter();

        this.chapter = chapter;
        isDownloaded = isChapterDownloaded(chapter);

        // If the chapter is partially read, set the starting page to the last the user read
        if (!chapter.read && chapter.last_page_read != 0)
            currentPage = chapter.last_page_read;
        else
            currentPage = 0;

        // Reset next and previous chapter. They have to be fetched again
        nextChapter = null;
        previousChapter = null;
        nextChapterPageList = null;

        start(GET_PAGE_LIST);
    }

    // Check whether the given chapter is downloaded
    public boolean isChapterDownloaded(Chapter chapter) {
        return downloadManager.isChapterDownloaded(source, manga, chapter);
    }

    public void retryPage(Page page) {
        page.setStatus(Page.QUEUE);
        retryPageSubject.onNext(page);
    }

    // Called before loading another chapter or leaving the reader. It allows to do operations
    // over the chapter read like saving progress
    private void onChapterLeft() {
        if (pageList == null)
            return;

        // Cache page list for online chapters to allow a faster reopen
        if (!isDownloaded)
            source.savePageList(chapter.url, pageList);

        // Save current progress of the chapter. Mark as read if the chapter is finished
        chapter.last_page_read = currentPage;
        if (isChapterFinished()) {
            chapter.read = true;
        }
        db.insertChapter(chapter).createObservable().subscribe();
    }

    // Check whether the chapter has been read
    private boolean isChapterFinished() {
        return !chapter.read && currentPage == pageList.size() - 1;
    }

    private void updateMangaSyncLastChapterRead() {
        if (pageList == null)
            return;

        db.getMangaSync(manga).createObservable()
                .take(1)
                .flatMap(Observable::from)
                .doOnNext(mangaSync -> {
                    MangaSyncService service = syncManager.getSyncService(mangaSync.sync_id);
                    if (!service.isLogged())
                        return;

                    int lastChapterReadLocal = 0;
                    // If the current chapter has been read, we check with this one
                    if (chapter.read)
                        lastChapterReadLocal = (int) Math.floor(chapter.chapter_number);
                    // If not, we check if the previous chapter has been read
                    else if (previousChapter != null && previousChapter.read)
                        lastChapterReadLocal = (int) Math.floor(previousChapter.chapter_number);

                    if (lastChapterReadLocal > mangaSync.last_chapter_read) {
                        mangaSync.last_chapter_read = lastChapterReadLocal;
                        UpdateMangaSyncService.start(getContext(), mangaSync);
                    }
                })
                .subscribe(next -> {},
                        error -> Timber.e(error.getCause(), error.getMessage()));
    }

    public void setCurrentPage(int currentPage) {
        this.currentPage = currentPage;
    }

    private void getAdjacentChapters() {
        add(db.getNextChapter(chapter).createObservable()
                .take(1)
                .flatMap(Observable::from)
                .subscribe(result -> nextChapter = result));

        add(db.getPreviousChapter(chapter).createObservable()
                .take(1)
                .flatMap(Observable::from)
                .subscribe(result -> previousChapter = result));
    }

    public void loadNextChapter() {
        if (hasNextChapter()) {
            onChapterLeft();
            loadChapter(nextChapter);
        }
    }

    public void loadPreviousChapter() {
        if (hasPreviousChapter()) {
            onChapterLeft();
            loadChapter(previousChapter);
        }
    }

    public boolean hasNextChapter() {
        return nextChapter != null;
    }

    public boolean hasPreviousChapter() {
        return previousChapter != null;
    }

    private void preloadNextChapter() {
        if (hasNextChapter() && !isChapterDownloaded(nextChapter)) {
            start(PRELOAD_NEXT_CHAPTER);
        }
    }

    private void stopPreloadingNextChapter() {
        if (isSubscribed(PRELOAD_NEXT_CHAPTER)) {
            stop(PRELOAD_NEXT_CHAPTER);
            if (nextChapterPageList != null)
                source.savePageList(nextChapter.url, nextChapterPageList);
        }
    }

    public void updateMangaViewer(int viewer) {
        manga.viewer = viewer;
        db.insertManga(manga).executeAsBlocking();
    }

    public Manga getManga() {
        return manga;
    }

}
