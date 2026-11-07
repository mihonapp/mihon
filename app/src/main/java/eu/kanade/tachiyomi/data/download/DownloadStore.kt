package eu.kanade.tachiyomi.data.download

import android.content.Context
import androidx.core.content.edit
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.data.download.model.Download
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mihon.domain.source.service.ChapterDownloadPlanner
import tachiyomi.domain.chapter.interactor.GetChapter
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager

/**
 * This class is used to persist active downloads across application restarts.
 */
@Inject
@SingleIn(AppScope::class)
class DownloadStore(
    context: Context,
    private val sourceManager: SourceManager,
    private val json: Json,
    private val getManga: GetManga,
    private val getChapter: GetChapter,
) {

    /**
     * Preference file where active downloads are stored.
     */
    private val preferences by lazy { context.getSharedPreferences("active_downloads", Context.MODE_PRIVATE) }

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
    suspend fun restore(queuedChapterIds: Set<Long> = emptySet()): List<Download> {
        val objs = preferences.all
            .mapNotNull { it.value as? String }
            .mapNotNull { deserialize(it) }
            .sortedBy { it.order }

        counter = maxOf(counter, (objs.maxOfOrNull { it.order } ?: -1) + 1)
        val downloads = mutableListOf<Download>()
        if (objs.isNotEmpty()) {
            val cachedManga = mutableMapOf<Long, Manga?>()
            val installed = sourceManager.getOnlineSources().associateBy { it.id }
            for (obj in objs) {
                if (obj.chapterId in queuedChapterIds) continue
                val manga = cachedManga.getOrPut(obj.mangaId) {
                    getManga.await(obj.mangaId)
                }
                val original = getChapter.await(obj.chapterId)
                if (manga == null || original == null) {
                    preferences.edit { remove(obj.chapterId.toString()) }
                    continue
                }
                val sourceId = ChapterDownloadPlanner.availableSources(
                    original,
                    manga.source,
                    installed.keys,
                    attemptedSourceIds = if (obj.failed) emptySet() else obj.attemptedSourceIds,
                ).firstOrNull() ?: continue
                val source = installed.getValue(sourceId)
                downloads.add(
                    Download(source, manga, original).apply {
                        attemptedSourceIds = obj.attemptedSourceIds
                        if (obj.failed) {
                            status = Download.State.ERROR
                        }
                    },
                )
            }
        }

        // Unrestorable entries stay persisted until an extension is available again.

        return downloads
    }

    /**
     * Converts a download to a string.
     *
     * @param download the download to serialize.
     */
    private fun serialize(download: Download): String {
        val obj = DownloadObject(
            download.manga.id,
            download.chapter.id,
            // Allocating a waiting download must not move it to the end after an app restart.
            preferences.getString(getKey(download), null)?.let(::deserialize)?.order ?: counter++,
            download.attemptedSourceIds,
            download.status == Download.State.ERROR,
        )
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
private data class DownloadObject(
    val mangaId: Long,
    val chapterId: Long,
    val order: Int,
    val attemptedSourceIds: Set<Long> = emptySet(),
    val failed: Boolean = false,
)
