package eu.kanade.mangafeed.data.helpers;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.util.List;

import eu.kanade.mangafeed.data.models.Chapter;
import eu.kanade.mangafeed.data.models.Manga;
import eu.kanade.mangafeed.data.models.Page;
import eu.kanade.mangafeed.events.DownloadChapterEvent;
import eu.kanade.mangafeed.sources.base.Source;
import eu.kanade.mangafeed.util.DiskUtils;
import rx.Observable;
import rx.Subscription;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;

public class DownloadManager {

    private PublishSubject<DownloadChapterEvent> downloadsSubject;
    private Subscription downloadsSubscription;

    private Context context;
    private SourceManager sourceManager;
    private PreferencesHelper preferences;

    public DownloadManager(Context context, SourceManager sourceManager, PreferencesHelper preferences) {
        this.context = context;
        this.sourceManager = sourceManager;
        this.preferences = preferences;

        initializeDownloadSubscription();
    }

    private void initializeDownloadSubscription() {
        if (downloadsSubscription != null && !downloadsSubscription.isUnsubscribed()) {
            downloadsSubscription.unsubscribe();
        }

        downloadsSubject = PublishSubject.create();

        downloadsSubscription = downloadsSubject
                .subscribeOn(Schedulers.io())
                .concatMap(event -> downloadChapter(event.getManga(), event.getChapter()))
                .onBackpressureBuffer()
                .subscribe();
    }

    public Observable<Page> downloadChapter(Manga manga, Chapter chapter) {
        final Source source = sourceManager.get(manga.source);
        final File chapterDirectory = getAbsoluteChapterDirectory(source, manga, chapter);

        return source
                .pullPageListFromNetwork(chapter.url)
                // Ensure we don't download a chapter already downloaded
                .filter(pages -> !isChapterDownloaded(chapterDirectory, pages))
                // Get all the URLs to the source images, fetch pages if necessary
                .flatMap(pageList -> Observable.merge(
                        Observable.from(pageList).filter(page -> page.getImageUrl() != null),
                        source.getRemainingImageUrlsFromPageList(pageList)))
                // Start downloading images
                .flatMap(page -> getDownloadedImage(page, source, chapterDirectory));
    }
    
    public File getAbsoluteChapterDirectory(Source source, Manga manga, Chapter chapter) {
        return new File(preferences.getDownloadsDirectory(),
                getChapterDirectory(source, manga, chapter));
    }

    public String getChapterDirectory(Source source, Manga manga, Chapter chapter) {
        return source.getName() +
                File.separator +
                manga.title.replaceAll("[^a-zA-Z0-9.-]", "_") +
                File.separator +
                chapter.name.replaceAll("[^a-zA-Z0-9.-]", "_");
    }

    private String getImageFilename(Page page) {
        return page.getImageUrl().substring(
                page.getImageUrl().lastIndexOf("/") + 1,
                page.getImageUrl().length());
    }

    private boolean isChapterDownloaded(File chapterDir, List<Page> pages) {
        return chapterDir.exists() && chapterDir.listFiles().length == pages.size();
    }

    private boolean isImageDownloaded(File imagePath) {
        return imagePath.exists() && !imagePath.isDirectory();
    }

    public Observable<Page> getDownloadedImage(final Page page, Source source, File chapterDir) {
        Observable<Page> obs = Observable.just(page);
        if (page.getImageUrl() == null)
            return obs;

        String imageFilename = getImageFilename(page);
        File imagePath = new File(chapterDir, imageFilename);

        if (!isImageDownloaded(imagePath)) {
            page.setStatus(Page.DOWNLOAD_IMAGE);
            obs = downloadImage(page, source, chapterDir, imageFilename);
        }

        return obs.flatMap(p -> {
            page.setImagePath(imagePath.getAbsolutePath());
            page.setStatus(Page.READY);
            return Observable.just(page);
        }).onErrorResumeNext(e -> {
            page.setStatus(Page.ERROR);
            return Observable.just(page);
        });
    }

    private Observable<Page> downloadImage(final Page page, Source source, File chapterDir, String imageFilename) {
        return source.getImageProgressResponse(page)
                .flatMap(resp -> {
                    try {
                        DiskUtils.saveBufferedSourceToDirectory(resp.body().source(), chapterDir, imageFilename);
                    } catch (IOException e) {
                        e.printStackTrace();
                        throw new IllegalStateException("Unable to save image");
                    }
                    return Observable.just(page);
                });
    }

    public PublishSubject<DownloadChapterEvent> getDownloadsSubject() {
        return downloadsSubject;
    }

}
