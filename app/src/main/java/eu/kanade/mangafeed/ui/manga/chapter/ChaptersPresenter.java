package eu.kanade.mangafeed.ui.manga.chapter;

import android.os.Bundle;

import java.util.List;

import javax.inject.Inject;

import de.greenrobot.event.EventBus;
import eu.kanade.mangafeed.data.database.DatabaseHelper;
import eu.kanade.mangafeed.data.database.models.Chapter;
import eu.kanade.mangafeed.data.database.models.Manga;
import eu.kanade.mangafeed.data.download.DownloadManager;
import eu.kanade.mangafeed.data.download.model.Download;
import eu.kanade.mangafeed.data.preference.PreferencesHelper;
import eu.kanade.mangafeed.data.source.SourceManager;
import eu.kanade.mangafeed.data.source.base.Source;
import eu.kanade.mangafeed.event.ChapterCountEvent;
import eu.kanade.mangafeed.event.DownloadChaptersEvent;
import eu.kanade.mangafeed.event.ReaderEvent;
import eu.kanade.mangafeed.ui.base.presenter.BasePresenter;
import eu.kanade.mangafeed.util.EventBusHook;
import eu.kanade.mangafeed.util.PostResult;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;
import timber.log.Timber;

public class ChaptersPresenter extends BasePresenter<ChaptersFragment> {

    @Inject DatabaseHelper db;
    @Inject SourceManager sourceManager;
    @Inject PreferencesHelper preferences;
    @Inject DownloadManager downloadManager;

    private Manga manga;
    private Source source;
    private List<Chapter> chapters;
    private boolean sortOrderAToZ = true;
    private boolean onlyUnread = true;
    private boolean onlyDownloaded;
    private boolean hasRequested;

    private PublishSubject<List<Chapter>> chaptersSubject;

    private static final int DB_CHAPTERS = 1;
    private static final int FETCH_CHAPTERS = 2;
    private static final int CHAPTER_STATUS_CHANGES = 3;

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        chaptersSubject = PublishSubject.create();

        restartableLatestCache(DB_CHAPTERS,
                this::getDbChaptersObs,
                ChaptersFragment::onNextChapters);

        restartableFirst(FETCH_CHAPTERS,
                this::getOnlineChaptersObs,
                (view, result) -> view.onFetchChaptersDone(),
                (view, error) -> view.onFetchChaptersError());

        restartableLatestCache(CHAPTER_STATUS_CHANGES,
                this::getChapterStatusObs,
                (view, download) -> view.onChapterStatusChange(download.chapter),
                (view, error) -> Timber.e(error.getCause(), error.getMessage()));

        registerForStickyEvents();
    }

    @Override
    protected void onDestroy() {
        unregisterForEvents();
        EventBus.getDefault().removeStickyEvent(ChapterCountEvent.class);
        super.onDestroy();
    }

    @EventBusHook
    public void onEventMainThread(Manga manga) {
        this.manga = manga;

        if (!isStarted(DB_CHAPTERS)) {
            source = sourceManager.get(manga.source);
            start(DB_CHAPTERS);

            add(db.getChapters(manga).createObservable()
                    .subscribeOn(Schedulers.io())
                    .doOnNext(chapters -> {
                        this.chapters = chapters;
                        EventBus.getDefault().postSticky(new ChapterCountEvent(chapters.size()));
                        for (Chapter chapter : chapters) {
                            setChapterStatus(chapter);
                        }
                        start(CHAPTER_STATUS_CHANGES);
                    })
                    .subscribe(chaptersSubject::onNext));
        }
    }

    public void fetchChaptersFromSource() {
        hasRequested = true;
        start(FETCH_CHAPTERS);
    }

    private void refreshChapters() {
        chaptersSubject.onNext(chapters);
    }

    private Observable<PostResult> getOnlineChaptersObs() {
        return source
                .pullChaptersFromNetwork(manga.url)
                .subscribeOn(Schedulers.io())
                .flatMap(chapters -> db.insertOrRemoveChapters(manga, chapters))
                .observeOn(AndroidSchedulers.mainThread());
    }

    private Observable<List<Chapter>> getDbChaptersObs() {
        return chaptersSubject
                .observeOn(Schedulers.io())
                .flatMap(this::applyChapterFilters)
                .observeOn(AndroidSchedulers.mainThread());
    }

    private Observable<List<Chapter>> applyChapterFilters(List<Chapter> chapters) {
        Observable<Chapter> observable = Observable.from(chapters);
        if (onlyUnread) {
            observable = observable.filter(chapter -> !chapter.read);
        }
        if (onlyDownloaded) {
            observable = observable.filter(chapter -> chapter.status == Download.DOWNLOADED);
        }
        return observable.toSortedList((chapter, chapter2) -> sortOrderAToZ ?
                Float.compare(chapter2.chapter_number, chapter.chapter_number) :
                Float.compare(chapter.chapter_number, chapter2.chapter_number));
    }

    private void setChapterStatus(Chapter chapter) {
        for (Download download : downloadManager.getQueue().get()) {
            if (chapter.id.equals(download.chapter.id)) {
                chapter.status = download.getStatus();
                return;
            }
        }

        if (downloadManager.isChapterDownloaded(source, manga, chapter)) {
            chapter.status = Download.DOWNLOADED;
        } else {
            chapter.status = Download.NOT_DOWNLOADED;
        }
    }

    private Observable<Download> getChapterStatusObs() {
        return downloadManager.getQueue().getStatusObservable()
                .observeOn(AndroidSchedulers.mainThread())
                .filter(download -> download.manga.id.equals(manga.id))
                .doOnNext(this::updateChapterStatus);
    }

    public void updateChapterStatus(Download download) {
        for (Chapter chapter : chapters) {
            if (download.chapter.id.equals(chapter.id)) {
                chapter.status = download.getStatus();
                break;
            }
        }
        if (onlyDownloaded && download.getStatus() == Download.DOWNLOADED)
            refreshChapters();
    }

    public Observable<Download> getDownloadProgressObs() {
        return downloadManager.getQueue().getProgressObservable()
                .filter(download -> download.manga.id.equals(manga.id))
                .observeOn(AndroidSchedulers.mainThread());
    }

    public void onOpenChapter(Chapter chapter) {
        EventBus.getDefault().postSticky(new ReaderEvent(source, manga, chapter));
    }

    public Chapter getNextUnreadChapter() {
        List<Chapter> chapters = db.getNextUnreadChapter(manga).executeAsBlocking();
        return !chapters.isEmpty() ? chapters.get(0) : null;
    }

    public void markChaptersRead(Observable<Chapter> selectedChapters, boolean read) {
        add(selectedChapters
                .subscribeOn(Schedulers.io())
                .map(chapter -> {
                    chapter.read = read;
                    if (!read) chapter.last_page_read = 0;
                    return chapter;
                })
                .toList()
                .flatMap(chapters -> db.insertChapters(chapters).createObservable())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe());
    }

    public void downloadChapters(Observable<Chapter> selectedChapters) {
        add(selectedChapters
                .toList()
                .subscribe(chapters -> {
                    EventBus.getDefault().postSticky(new DownloadChaptersEvent(manga, chapters));
                }));
    }

    public void deleteChapters(Observable<Chapter> selectedChapters) {
        add(selectedChapters
                .subscribe(chapter -> {
                    downloadManager.getQueue().remove(chapter);
                }, error -> {
                    Timber.e(error.getMessage());
                }, () -> {
                    if (onlyDownloaded)
                        refreshChapters();
                }));
    }

    public void deleteChapter(Chapter chapter) {
        downloadManager.deleteChapter(source, manga, chapter);
    }

    public void revertSortOrder() {
        //TODO manga.chapter_order
        sortOrderAToZ = !sortOrderAToZ;
        refreshChapters();
    }

    public void setReadFilter(boolean onlyUnread) {
        //TODO do we need save filter for manga?
        this.onlyUnread = onlyUnread;
        refreshChapters();
    }

    public void setDownloadedFilter(boolean onlyDownloaded) {
        this.onlyDownloaded = onlyDownloaded;
        refreshChapters();
    }

    public boolean getSortOrder() {
        return sortOrderAToZ;
    }

    public boolean getReadFilter() {
        return onlyUnread;
    }

    public boolean getDownloadedFilter() {
        return onlyDownloaded;
    }

    public Manga getManga() {
        return manga;
    }

    public List<Chapter> getChapters() {
        return chapters;
    }

    public boolean hasRequested() {
        return hasRequested;
    }

}
