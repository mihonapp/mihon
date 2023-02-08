package eu.kanade.tachiyomi.data.download.model

import eu.kanade.tachiyomi.data.download.DownloadStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga

class DownloadQueue(
    private val store: DownloadStore,
) {
    private val _state = MutableStateFlow<List<Download>>(emptyList())
    val state = _state.asStateFlow()

    fun addAll(downloads: List<Download>) {
        _state.update {
            downloads.forEach { download ->
                download.status = Download.State.QUEUE
            }
            store.addAll(downloads)
            it + downloads
        }
    }

    fun remove(download: Download) {
        _state.update {
            store.remove(download)
            if (download.status == Download.State.DOWNLOADING || download.status == Download.State.QUEUE) {
                download.status = Download.State.NOT_DOWNLOADED
            }
            it - download
        }
    }

    fun remove(chapter: Chapter) {
        _state.value.find { it.chapter.id == chapter.id }?.let { remove(it) }
    }

    fun remove(chapters: List<Chapter>) {
        chapters.forEach(::remove)
    }

    fun remove(manga: Manga) {
        _state.value.filter { it.manga.id == manga.id }.forEach { remove(it) }
    }

    fun clear() {
        _state.update {
            it.forEach { download ->
                if (download.status == Download.State.DOWNLOADING || download.status == Download.State.QUEUE) {
                    download.status = Download.State.NOT_DOWNLOADED
                }
            }
            store.clear()
            emptyList()
        }
    }

    fun statusFlow(): Flow<Download> = state
        .flatMapLatest { downloads ->
            downloads
                .map { download ->
                    download.statusFlow.drop(1).map { download }
                }
                .merge()
        }
        .onStart { emitAll(getActiveDownloads()) }

    fun progressFlow(): Flow<Download> = state
        .flatMapLatest { downloads ->
            downloads
                .map { download ->
                    download.progressFlow.drop(1).map { download }
                }
                .merge()
        }
        .onStart { emitAll(getActiveDownloads()) }

    private fun getActiveDownloads(): Flow<Download> =
        _state.value.filter { download -> download.status == Download.State.DOWNLOADING }.asFlow()

    fun count(predicate: (Download) -> Boolean) = _state.value.count(predicate)
    fun filter(predicate: (Download) -> Boolean) = _state.value.filter(predicate)
    fun find(predicate: (Download) -> Boolean) = _state.value.find(predicate)
    fun <K> groupBy(keySelector: (Download) -> K) = _state.value.groupBy(keySelector)
    fun isEmpty() = _state.value.isEmpty()
    fun isNotEmpty() = _state.value.isNotEmpty()
    fun none(predicate: (Download) -> Boolean) = _state.value.none(predicate)
    fun toMutableList() = _state.value.toMutableList()
}
