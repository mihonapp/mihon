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

public class ChaptersPresenter extends BasePresenter<ChaptersFragment> {

    @Inject DatabaseHelper db;
    @Inject SourceManager sourceManager;
    @Inject PreferencesHelper preferences;
    @Inject DownloadManager downloadManager;

    private Manga manga;
    private Source source;
    private List<Chapter> chapters;
    private boolean isCatalogueManga;
    private boolean sortOrderAToZ = true;
    private boolean onlyUnread = true;
    private boolean onlyDownloaded;

    private PublishSubject<List<Chapter>> chaptersSubject;

    private static final int DB_CHAPTERS = 1;
    private static final int FETCH_CHAPTERS = 2;

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        chaptersSubject = PublishSubject.create();

        restartableLatestCache(DB_CHAPTERS,
                this::getDbChaptersObs,
                ChaptersFragment::onNextChapters
        );

        restartableLatestCache(FETCH_CHAPTERS,
                this::getOnlineChaptersObs,
                (view, result) -> view.onFetchChaptersFinish()
        );
    }

    @Override
    protected void onTakeView(ChaptersFragment view) {
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
        EventBus.getDefault().removeStickyEvent(ChapterCountEvent.class);
    }

    @EventBusHook
    public void onEventMainThread(Manga manga) {
        if (this.manga == null) {
            this.manga = manga;
            source = sourceManager.get(manga.source);
            start(DB_CHAPTERS);

            add(db.getChapters(manga).createObservable()
                    .subscribeOn(Schedulers.io())
                    .doOnNext(chapters -> {
                        this.chapters = chapters;
                        EventBus.getDefault().postSticky(new ChapterCountEvent(chapters.size()));
                    })
                    .subscribe(chaptersSubject::onNext));

            // Get chapters if it's an online source
            if (isCatalogueManga) {
                fetchChapters();
            }
        }
    }

    public void fetchChapters() {
        start(FETCH_CHAPTERS);
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

        observable = observable.doOnNext(this::setChapterStatus);
        if (onlyDownloaded) {
            observable = observable.filter(chapter -> chapter.status == Download.DOWNLOADED);
        }
        return observable.toSortedList((chapter, chapter2) -> {
            if (sortOrderAToZ) {
                return Float.compare(chapter.chapter_number, chapter2.chapter_number);
            } else {
                return Float.compare(chapter2.chapter_number, chapter.chapter_number);
            }
        });
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
                    downloadManager.deleteChapter(source, manga, chapter);
                    chapter.status = Download.NOT_DOWNLOADED;
                }));
    }

    public void revertSortOrder() {
        //TODO manga.chapter_order
        sortOrderAToZ = !sortOrderAToZ;
        chaptersSubject.onNext(chapters);
    }

    public void setReadFilter(boolean onlyUnread) {
        //TODO do we need save filter for manga?
        this.onlyUnread = onlyUnread;
        chaptersSubject.onNext(chapters);
    }

    public void setDownloadedFilter(boolean onlyDownloaded) {
        this.onlyDownloaded = onlyDownloaded;
        chaptersSubject.onNext(chapters);
    }

    public void setIsCatalogueManga(boolean value) {
        isCatalogueManga = value;
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

}
