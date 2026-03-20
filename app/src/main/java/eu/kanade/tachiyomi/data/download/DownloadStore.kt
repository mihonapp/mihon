package eu.kanade.tachiyomi.data.download

import android.content.Context
import androidx.core.content.edit
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tachiyomi.domain.chapter.interactor.GetChapter
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * This class is used to persist active downloads across application restarts.
 */
class DownloadStore(
    context: Context,
    private val sourceManager: SourceManager = Injekt.get(),
    private val json: Json = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val getChapter: GetChapter = Injekt.get(),
) {

    /**
     * Preference file where active downloads are stored.
     */
    private val preferences = context.getSharedPreferences("active_downloads", Context.MODE_PRIVATE)

    /**
     * Counter used to keep the queue order.
     */
    private var counter = 0

    /**
     * Adds a list of downloads to the store.
     *
     * @param downloads the list of downloads to add.
     */
    fun addAll(downloads: List<Download>) {
        preferences.edit {
            downloads.forEach { putString(getKey(it), serialize(it)) }
        }
    }

    /**
     * Removes a download from the store.
     *
     * @param download the download to remove.
     */
    fun remove(download: Download) {
        preferences.edit {
            remove(getKey(download))
        }
    }

    /**
     * Removes a list of downloads from the store.
     *
     * @param downloads the download to remove.
     */
    fun removeAll(downloads: List<Download>) {
        preferences.edit {
            downloads.forEach { remove(getKey(it)) }
        }
    }

    /**
     * Removes all the downloads from the store.
     */
    fun clear() {
        preferences.edit {
            clear()
        }
    }

    /**
     * Returns the preference's key for the given download.
     *
     * @param download the download.
     */
    private fun getKey(download: Download): String {
        return download.chapter.id.toString()
    }

    /**
     * Returns the list of downloads to restore. It should be called in a background thread.
     */
    fun restore(): List<Download> {
        val objs = preferences.all
            .mapNotNull { it.value as? String }
            .mapNotNull { deserialize(it) }
            .sortedBy { it.order }

        val downloads = mutableListOf<Download>()
        if (objs.isNotEmpty()) {
            val cachedManga = mutableMapOf<Long, Manga?>()
            for ((mangaId, chapterId) in objs) {
                val manga = cachedManga.getOrPut(mangaId) {
                    runBlocking { getManga.await(mangaId) }
                } ?: continue
                val source = sourceManager.get(manga.source) as? HttpSource ?: continue
                val chapter = runBlocking { getChapter.await(chapterId) } ?: continue
                downloads.add(Download(source, manga, chapter))
            }
        }

        // Clear the store, downloads will be added again immediately.
        clear()
        return downloads
    }

    /**
     * Converts a download to a string.
     *
     * @param download the download to serialize.
     */
    private fun serialize(download: Download): String {
        val obj = DownloadObject(download.manga.id, download.chapter.id, counter++)
        return json.encodeToString(obj)
    }

    /**
     * Restore a download from a string.
     *
     * @param string the download as string.
     */
    private fun deserialize(string: String): DownloadObject? {
        return try {
            json.decodeFromString<DownloadObject>(string)
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Class used for download serialization
 *
 * @param mangaId the id of the manga.
 * @param chapterId the id of the chapter.
 * @param order the order of the download in the queue.
 */
@Serializable
private data class DownloadObject(val mangaId: Long, val chapterId: Long, val order: Int)
