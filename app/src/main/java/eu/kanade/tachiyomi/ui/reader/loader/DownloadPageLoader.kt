package eu.kanade.tachiyomi.ui.reader.loader

import android.app.Application
import android.net.Uri
import com.hippo.unifile.UniFile
import eu.kanade.domain.manga.model.Manga
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.io.File

/**
 * Loader used to load a chapter from the downloaded chapters.
 */
class DownloadPageLoader(
    private val chapter: ReaderChapter,
    private val manga: Manga,
    private val source: Source,
    private val downloadManager: DownloadManager,
    private val downloadProvider: DownloadProvider,
) : PageLoader() {

    // Needed to open input streams
    private val context: Application by injectLazy()

    /**
     * Returns an observable containing the pages found on this downloaded chapter.
     */
    override fun getPages(): Observable<List<ReaderPage>> {
        val dbChapter = chapter.chapter
        val chapterPath = downloadProvider.findChapterDir(dbChapter.name, dbChapter.scanlator, manga.title, source)
        return if (chapterPath?.isFile == true) {
            getPagesFromArchive(chapterPath)
        } else {
            getPagesFromDirectory()
        }
    }

    private fun getPagesFromArchive(chapterPath: UniFile): Observable<List<ReaderPage>> {
        val loader = ZipPageLoader(File(chapterPath.filePath!!))
        return loader.getPages()
    }

    private fun getPagesFromDirectory(): Observable<List<ReaderPage>> {
        return downloadManager.buildPageList(source, manga, chapter.chapter)
            .map { pages ->
                pages.map { page ->
                    ReaderPage(page.index, page.url, page.imageUrl) {
                        context.contentResolver.openInputStream(page.uri ?: Uri.EMPTY)!!
                    }.apply {
                        status = Page.READY
                    }
                }
            }
    }

    override fun getPage(page: ReaderPage): Observable<Int> {
        return Observable.just(Page.READY)
    }
}
