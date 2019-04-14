package eu.kanade.tachiyomi.ui.reader.loader

import com.elvishew.xlog.XLog
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import exh.debug.DebugFunctions.prefs
import rx.Completable
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import timber.log.Timber

/**
 * Loader used to retrieve the [PageLoader] for a given chapter.
 */
class ChapterLoader(
        private val downloadManager: DownloadManager,
        private val manga: Manga,
        private val source: Source
) {

    /**
     * Returns a completable that assigns the page loader and loads the its pages. It just
     * completes if the chapter is already loaded.
     */
    fun loadChapter(chapter: ReaderChapter): Completable {
        if (chapter.state is ReaderChapter.State.Loaded) {
            return Completable.complete()
        }

        return Observable.just(chapter)
            .doOnNext { chapter.state = ReaderChapter.State.Loading }
            .observeOn(Schedulers.io())
            .flatMap {
                Timber.d("Loading pages for ${chapter.chapter.name}")

                val loader = getPageLoader(it)

                loader.getPages().take(1).doOnNext { pages ->
                    pages.forEach { it.chapter = chapter }
                }.map { pages -> loader to pages }
            }
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext { (loader, pages) ->
                if (pages.isEmpty()) {
                    throw Exception("Page list is empty")
                }

                chapter.pageLoader = loader // Assign here to fix race with unref
                chapter.state = ReaderChapter.State.Loaded(pages)

                // If the chapter is partially read, set the starting page to the last the user read
                // otherwise use the requested page.
                if (!chapter.chapter.read /* --> EH */ || prefs
                                .eh_preserveReadingPosition()
                                .getOrDefault() /* <-- EH */) {
                    chapter.requestedPage = chapter.chapter.last_page_read
                }
            }
            .toCompletable()
            .doOnError {
                // [EXH]
                XLog.w("> Failed to fetch page list!", it)
                XLog.w("> (source.id: %s, source.name: %s, manga.id: %s, manga.url: %s, chapter.id: %s, chapter.url: %s)",
                        source.id,
                        source.name,
                        manga.id,
                        manga.url,
                        chapter.chapter.id,
                        chapter.chapter.url)

                chapter.state = ReaderChapter.State.Error(it)
            }
    }

    /**
     * Returns the page loader to use for this [chapter].
     */
    private fun getPageLoader(chapter: ReaderChapter): PageLoader {
        val isDownloaded = downloadManager.isChapterDownloaded(chapter.chapter, manga, true)
        return when {
            isDownloaded -> DownloadPageLoader(chapter, manga, source, downloadManager)
            source is HttpSource -> HttpPageLoader(chapter, source)
            source is LocalSource -> source.getFormat(chapter.chapter).let { format ->
                when (format) {
                    is LocalSource.Format.Directory -> DirectoryPageLoader(format.file)
                    is LocalSource.Format.Zip -> ZipPageLoader(format.file)
                    is LocalSource.Format.Rar -> RarPageLoader(format.file)
                    is LocalSource.Format.Epub -> EpubPageLoader(format.file)
                }
            }
            else -> error("Loader not implemented")
        }
    }

}
