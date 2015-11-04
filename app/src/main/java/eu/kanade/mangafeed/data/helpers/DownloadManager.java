package eu.kanade.mangafeed.data.helpers;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import eu.kanade.mangafeed.data.models.Chapter;
import eu.kanade.mangafeed.data.models.Download;
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
    private Subscription downloadSubscription;

    private Context context;
    private SourceManager sourceManager;
    private PreferencesHelper preferences;
    private Gson gson;

    private List<Download> queue;

    public DownloadManager(Context context, SourceManager sourceManager, PreferencesHelper preferences) {
        this.context = context;
        this.sourceManager = sourceManager;
        this.preferences = preferences;
        this.gson = new Gson();

        queue = new ArrayList<>();

        initializeDownloadSubscription();
    }

    public PublishSubject<DownloadChapterEvent> getDownloadsSubject() {
        return downloadsSubject;
    }

    private void initializeDownloadSubscription() {
        if (downloadSubscription != null && !downloadSubscription.isUnsubscribed()) {
            downloadSubscription.unsubscribe();
        }

        downloadsSubject = PublishSubject.create();

        // Listen for download events, add them to queue and download
        downloadSubscription = downloadsSubject
                .subscribeOn(Schedulers.io())
                .filter(event -> !isChapterDownloaded(event))
                .flatMap(this::createDownload)
                .window(preferences.getDownloadThreads())
                .concatMap(concurrentDownloads -> concurrentDownloads
                        .concatMap(this::downloadChapter))
                .onBackpressureBuffer()
                .subscribe();
    }

    // Check if a chapter is already downloaded
    private boolean isChapterDownloaded(DownloadChapterEvent event) {
        final Source source = sourceManager.get(event.getManga().source);

        // If the chapter is already queued, don't add it again
        for (Download download : queue) {
            if (download.chapter.id == event.getChapter().id)
                return true;
        }

        // If the directory doesn't exist, the chapter isn't downloaded
        File dir = getAbsoluteChapterDirectory(source, event.getManga(), event.getChapter());
        if (!dir.exists())
            return false;

        // If the page list doesn't exist, the chapter isn't download (or maybe it's,
        // but we consider it's not)
        List<Page> savedPages = getSavedPageList(source, event.getManga(), event.getChapter());
        if (savedPages == null)
            return false;

        // If the number of files matches the number of pages, the chapter is downloaded.
        // We have the index file, so we check one file less
        return (dir.listFiles().length - 1) == savedPages.size();
    }

    // Create a download object and add it to the downloads queue
    private Observable<Download> createDownload(DownloadChapterEvent event) {
        Download download = new Download(
                sourceManager.get(event.getManga().source),
                event.getManga(),
                event.getChapter());

        download.directory = getAbsoluteChapterDirectory(
                download.source, download.manga, download.chapter);

        queue.add(download);
        return Observable.just(download);
    }

    // Download the entire chapter
    private Observable<Page> downloadChapter(Download download) {
        return download.source
                .pullPageListFromNetwork(download.chapter.url)
                .subscribeOn(Schedulers.io())
                // Add resulting pages to download object
                .doOnNext(pages -> download.pages = pages)
                // Get all the URLs to the source images, fetch pages if necessary
                .flatMap(pageList -> Observable.merge(
                        Observable.from(pageList).filter(page -> page.getImageUrl() != null),
                        download.source.getRemainingImageUrlsFromPageList(pageList)))
                // Start downloading images, consider we can have downloaded images already
                .concatMap(page -> getDownloadedImage(page, download.source, download.directory))
                // Remove from the queue
                .doOnCompleted(() -> removeFromQueue(download));
    }

    // Get downloaded image if exists, otherwise download it with the method below
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

    // Download the image
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

    // Get the filename for an image given the page
    private String getImageFilename(Page page) {
        return page.getImageUrl().substring(
                page.getImageUrl().lastIndexOf("/") + 1,
                page.getImageUrl().length());
    }

    private boolean isImageDownloaded(File imagePath) {
        return imagePath.exists() && !imagePath.isDirectory();
    }

    private void removeFromQueue(final Download download) {
        savePageList(download.source, download.manga, download.chapter, download.pages);
        queue.remove(download);
    }

    // Return the page list from the chapter's directory if it exists, null otherwise
    public List<Page> getSavedPageList(Source source, Manga manga, Chapter chapter) {
        File chapterDir = getAbsoluteChapterDirectory(source, manga, chapter);
        File pagesFile = new File(chapterDir, "index.json");

        try {
            JsonReader reader = new JsonReader(new FileReader(pagesFile.getAbsolutePath()));

            Type collectionType = new TypeToken<List<Page>>() {}.getType();
            return gson.fromJson(reader, collectionType);
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    // Save the page list to the chapter's directory
    public void savePageList(Source source, Manga manga, Chapter chapter, List<Page> pages) {
        File chapterDir = getAbsoluteChapterDirectory(source, manga, chapter);
        File pagesFile = new File(chapterDir, "index.json");

        FileOutputStream out;
        try {
            out = new FileOutputStream(pagesFile);
            out.write(gson.toJson(pages).getBytes());
            out.flush();
            out.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Get the absolute path to the chapter directory
    public File getAbsoluteChapterDirectory(Source source, Manga manga, Chapter chapter) {
        String chapterRelativePath = source.getName() +
                File.separator +
                manga.title.replaceAll("[^a-zA-Z0-9.-]", "_") +
                File.separator +
                chapter.name.replaceAll("[^a-zA-Z0-9.-]", "_");

        return new File(preferences.getDownloadsDirectory(), chapterRelativePath);
    }

}
