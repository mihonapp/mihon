package eu.kanade.tachiyomi.data.download.model

import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.source.model.Page
import rx.Observable
import rx.subjects.PublishSubject
import java.util.*

class DownloadQueue : ArrayList<Download>() {

    private val statusSubject = PublishSubject.create<Download>()

    override fun add(download: Download): Boolean {
        download.setStatusSubject(statusSubject)
        download.status = Download.QUEUE
        return super.add(download)
    }

    fun del(download: Download) {
        super.remove(download)
        download.setStatusSubject(null)
    }

    fun del(chapter: Chapter) {
        for (download in this) {
            if (download.chapter.id == chapter.id) {
                del(download)
                break
            }
        }
    }

    fun getActiveDownloads() =
        Observable.from(this).filter { download -> download.status == Download.DOWNLOADING }

    fun getStatusObservable() = statusSubject.onBackpressureBuffer()

    fun getProgressObservable(): Observable<Download> {
        return statusSubject.onBackpressureBuffer()
                .startWith(getActiveDownloads())
                .flatMap { download ->
                    if (download.status == Download.DOWNLOADING) {
                        val pageStatusSubject = PublishSubject.create<Int>()
                        setPagesSubject(download.pages, pageStatusSubject)
                        return@flatMap pageStatusSubject
                                .filter { it == Page.READY }
                                .map { download }

                    } else if (download.status == Download.DOWNLOADED || download.status == Download.ERROR) {
                        setPagesSubject(download.pages, null)
                    }
                    Observable.just(download)
                }
                .filter { it.status == Download.DOWNLOADING }
    }

    private fun setPagesSubject(pages: List<Page>?, subject: PublishSubject<Int>?) {
        if (pages != null) {
            for (page in pages) {
                page.setStatusSubject(subject)
            }
        }
    }

}
