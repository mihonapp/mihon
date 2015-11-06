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
import eu.kanade.mangafeed.data.models.DownloadQueue;
import eu.kanade.mangafeed.data.models.Manga;
import eu.kanade.mangafeed.data.models.Page;
import eu.kanade.mangafeed.events.DownloadChaptersEvent;
import eu.kanade.mangafeed.sources.base.Source;
import eu.kanade.mangafeed.util.DiskUtils;
import eu.kanade.mangafeed.util.DynamicConcurrentMergeOperator;
import rx.Observable;
import rx.Subscription;
import rx.schedulers.Schedulers;
import rx.subjects.BehaviorSubject;
import rx.subjects.PublishSubject;
import timber.log.Timber;

public class DownloadManager {

    private PublishSubject<DownloadChaptersEvent> downloadsSubject;
    private Subscription downloadSubscription;
    private Subscription threadNumberSubscription;

    private Context context;
    private SourceManager sourceManager;
    private PreferencesHelper preferences;
    private Gson gson;

    private DownloadQueue queue;

    public static final String PAGE_LIST_FILE = "index.json";

    public DownloadManager(Context context, SourceManager sourceManager, PreferencesHelper preferences) {
        this.context = context;
        this.sourceManager = sourceManager;
        this.preferences = preferences;
        this.gson = new Gson();

        queue = new DownloadQueue();

        initializeDownloadSubscription();
    }

    public PublishSubject<DownloadChaptersEvent> getDownloadsSubject() {
        return downloadsSubject;
    }

    private void initializeDownloadSubscription() {
        if (downloadSubscription != null && !downloadSubscription.isUnsubscribed()) {
            downloadSubscription.unsubscribe();
        }

        if (threadNumberSubscription != null && !threadNumberSubscription.isUnsubscribed())
            threadNumberSubscription.unsubscribe();

        downloadsSubject = PublishSubject.create();
        BehaviorSubject<Integer> threads = BehaviorSubject.create();

        threadNumberSubscription = preferences.getDownloadTheadsObs()
                .subscribe(threads::onNext);

        // Listen for download events, add them to queue and download
        downloadSubscription = downloadsSubject
                .subscribeOn(Schedulers.io())
                .flatMap(this::prepareDownloads)
                .lift(new DynamicConcurrentMergeOperator<>(this::downloadChapter, threads))
                .onBackpressureBuffer()
                .subscribe(page -> {},
                        e -> Timber.e(e.fillInStackTrace(), e.getMessage()));
    }

    // Create a download object for every chapter and add it to the downloads queue
    private Observable<Download> prepareDownloads(DownloadChaptersEvent event) {
        final Manga manga = event.getManga();
        final Source source = sourceManager.get(manga.source);
        List<Download> downloads = new ArrayList<>();

        for (Chapter chapter : event.getChapters()) {
            Download download = new Download(source, manga, chapter);

            if (!isChapterDownloaded(download)) {
                queue.add(download);
                downloads.add(download);
            }
        }

        return Observable.from(downloads);
    }

    // Check if a chapter is already downloaded
    private boolean isChapterDownloaded(Download download) {
        // If the chapter is already queued, don't add it again
        for (Download queuedDownload : queue.get()) {
            if (download.chapter.id == queuedDownload.chapter.id)
                return true;
        }

        // Add the directory to the download object for future access
        download.directory = getAbsoluteChapterDirectory(download);

        // If the directory doesn't exist, the chapter isn't downloaded. Create it in this case
        if (!download.directory.exists()) {
            // FIXME Sometimes it's failing to create the directory... My fault?
            try {
                DiskUtils.createDirectory(download.directory);
            } catch (IOException e) {
                Timber.e("Unable to create directory for chapter");
            }
            return false;
        }


        // If the page list doesn't exist, the chapter isn't download (or maybe it's,
        // but we consider it's not)
        List<Page> savedPages = getSavedPageList(download);
        if (savedPages == null)
            return false;

        // Add the page list to the download object for future access
        download.pages = savedPages;

        // If the number of files matches the number of pages, the chapter is downloaded.
        // We have the index file, so we check one file more
        return savedPages.size() + 1 == download.directory.listFiles().length;
    }

    // Download the entire chapter
    private Observable<Page> downloadChapter(Download download) {
        Observable<List<Page>> pageListObservable = download.pages == null ?
                // Pull page list from network and add them to download object
                download.source
                        .pullPageListFromNetwork(download.chapter.url)
                        .doOnNext(pages -> download.pages = pages)
                        .doOnNext(pages -> savePageList(download)) :
                // Or if the file exists, start from here
                Observable.just(download.pages);

        return pageListObservable
                .subscribeOn(Schedulers.io())
                .doOnNext(pages -> download.setStatus(Download.DOWNLOADING))
                // Get all the URLs to the source images, fetch pages if necessary
                .flatMap(pageList -> Observable.merge(
                        Observable.from(pageList).filter(page -> page.getImageUrl() != null),
                        download.source.getRemainingImageUrlsFromPageList(pageList)))
                // Start downloading images, consider we can have downloaded images already
                .concatMap(page -> getDownloadedImage(page, download.source, download.directory))
                // Do after download completes
                .doOnCompleted(() -> onChapterDownloaded(download));
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
                        Timber.e(e.fillInStackTrace(), e.getMessage());
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

    private void onChapterDownloaded(final Download download) {
        download.setStatus(Download.DOWNLOADED);
        download.totalProgress = download.pages.size() * 100;
        savePageList(download.source, download.manga, download.chapter, download.pages);
    }

    // Return the page list from the chapter's directory if it exists, null otherwise
    public List<Page> getSavedPageList(Source source, Manga manga, Chapter chapter) {
        File chapterDir = getAbsoluteChapterDirectory(source, manga, chapter);
        File pagesFile = new File(chapterDir, PAGE_LIST_FILE);

        try {
            if (pagesFile.exists()) {
                JsonReader reader = new JsonReader(new FileReader(pagesFile.getAbsolutePath()));

                Type collectionType = new TypeToken<List<Page>>() {}.getType();
                return gson.fromJson(reader, collectionType);
            }
        } catch (FileNotFoundException e) {
            Timber.e(e.fillInStackTrace(), e.getMessage());
        }
        return null;
    }

    // Shortcut for the method above
    private List<Page> getSavedPageList(Download download) {
        return getSavedPageList(download.source, download.manga, download.chapter);
    }

    // Save the page list to the chapter's directory
    public void savePageList(Source source, Manga manga, Chapter chapter, List<Page> pages) {
        File chapterDir = getAbsoluteChapterDirectory(source, manga, chapter);
        File pagesFile = new File(chapterDir, PAGE_LIST_FILE);

        FileOutputStream out;
        try {
            out = new FileOutputStream(pagesFile);
            out.write(gson.toJson(pages).getBytes());
            out.flush();
            out.close();
        } catch (Exception e) {
            Timber.e(e.fillInStackTrace(), e.getMessage());
        }
    }

    // Shortcut for the method above
    private void savePageList(Download download) {
        savePageList(download.source, download.manga, download.chapter, download.pages);
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

    // Shortcut for the method above
    private File getAbsoluteChapterDirectory(Download download) {
        return getAbsoluteChapterDirectory(download.source, download.manga, download.chapter);
    }

    public void deleteChapter(Source source, Manga manga, Chapter chapter) {
        File path = getAbsoluteChapterDirectory(source, manga, chapter);
        DiskUtils.deleteFiles(path);
    }

    public DownloadQueue getQueue() {
        return queue;
    }
}
