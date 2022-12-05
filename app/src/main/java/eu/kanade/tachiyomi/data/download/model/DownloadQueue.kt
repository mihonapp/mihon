package eu.kanade.tachiyomi.data.download.model

import eu.kanade.core.util.asFlow
import eu.kanade.domain.chapter.model.Chapter
import eu.kanade.domain.manga.model.Manga
import eu.kanade.tachiyomi.data.download.DownloadStore
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.util.lang.launchNonCancellable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import rx.Observable
import rx.subjects.PublishSubject
import java.util.concurrent.CopyOnWriteArrayList

class DownloadQueue(
    private val store: DownloadStore,
    private val queue: MutableList<Download> = CopyOnWriteArrayList(),
) : List<Download> by queue {

    private val scope = CoroutineScope(Dispatchers.IO)

    private val statusSubject = PublishSubject.create<Download>()

    private val _updates: Channel<Unit> = Channel(Channel.UNLIMITED)
    val updates = _updates.receiveAsFlow().onStart { emit(Unit) }.map { queue }

    fun addAll(downloads: List<Download>) {
        downloads.forEach { download ->
            download.statusSubject = statusSubject
            download.statusCallback = ::setPagesFor
            download.status = Download.State.QUEUE
        }
        queue.addAll(downloads)
        store.addAll(downloads)
        scope.launchNonCancellable {
            _updates.send(Unit)
        }
    }

    fun remove(download: Download) {
        val removed = queue.remove(download)
        store.remove(download)
        download.statusSubject = null
        download.statusCallback = null
        if (download.status == Download.State.DOWNLOADING || download.status == Download.State.QUEUE) {
            download.status = Download.State.NOT_DOWNLOADED
        }
        if (removed) {
            scope.launchNonCancellable {
                _updates.send(Unit)
            }
        }
    }

    fun remove(chapter: Chapter) {
        find { it.chapter.id == chapter.id }?.let { remove(it) }
    }

    fun remove(chapters: List<Chapter>) {
        chapters.forEach(::remove)
    }

    fun remove(manga: Manga) {
        filter { it.manga.id == manga.id }.forEach { remove(it) }
    }

    fun clear() {
        queue.forEach { download ->
            download.statusSubject = null
            download.statusCallback = null
            if (download.status == Download.State.DOWNLOADING || download.status == Download.State.QUEUE) {
                download.status = Download.State.NOT_DOWNLOADED
            }
        }
        queue.clear()
        store.clear()
        scope.launchNonCancellable {
            _updates.send(Unit)
        }
    }

    fun statusFlow(): Flow<Download> = getStatusObservable().asFlow()

    fun progressFlow(): Flow<Download> = getProgressObservable().asFlow()

    private fun getActiveDownloads(): Observable<Download> =
        Observable.from(this).filter { download -> download.status == Download.State.DOWNLOADING }

    private fun getStatusObservable(): Observable<Download> = statusSubject
        .startWith(getActiveDownloads())
        .onBackpressureBuffer()

    private fun getProgressObservable(): Observable<Download> {
        return statusSubject.onBackpressureBuffer()
            .startWith(getActiveDownloads())
            .flatMap { download ->
                if (download.status == Download.State.DOWNLOADING) {
                    val pageStatusSubject = PublishSubject.create<Int>()
                    setPagesSubject(download.pages, pageStatusSubject)
                    return@flatMap pageStatusSubject
                        .onBackpressureBuffer()
                        .filter { it == Page.READY }
                        .map { download }
                } else if (download.status == Download.State.DOWNLOADED || download.status == Download.State.ERROR) {
                    setPagesSubject(download.pages, null)
                }
                Observable.just(download)
            }
            .filter { it.status == Download.State.DOWNLOADING }
    }

    private fun setPagesFor(download: Download) {
        if (download.status == Download.State.DOWNLOADED || download.status == Download.State.ERROR) {
            setPagesSubject(download.pages, null)
        }
    }

    private fun setPagesSubject(pages: List<Page>?, subject: PublishSubject<Int>?) {
        pages?.forEach { it.setStatusSubject(subject) }
    }
}
