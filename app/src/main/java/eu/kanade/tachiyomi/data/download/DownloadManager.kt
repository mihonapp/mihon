package eu.kanade.tachiyomi.data.download

import android.content.Context
import eu.kanade.domain.download.service.DownloadPreferences
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.download.model.DownloadQueue
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.Page
import kotlinx.coroutines.runBlocking
import logcat.LogPriority
import tachiyomi.core.util.lang.launchIO
import tachiyomi.core.util.system.logcat
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * This class is used to manage chapter downloads in the application. It must be instantiated once
 * and retrieved through dependency injection. You can use this class to queue new chapters or query
 * downloaded chapters.
 */
class DownloadManager(
    private val context: Context,
    private val provider: DownloadProvider = Injekt.get(),
    private val cache: DownloadCache = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val downloadPreferences: DownloadPreferences = Injekt.get(),
) {

    /**
     * Downloader whose only task is to download chapters.
     */
    private val downloader = Downloader(context, provider, cache)

    /**
     * Queue to delay the deletion of a list of chapters until triggered.
     */
    private val pendingDeleter = DownloadPendingDeleter(context)

    /**
     * Downloads queue, where the pending chapters are stored.
     */
    val queue: DownloadQueue
        get() = downloader.queue

    /**
     * Tells the downloader to begin downloads.
     *
     * @return true if it's started, false otherwise (empty queue).
     */
    fun startDownloads(): Boolean {
        return downloader.start()
    }

    /**
     * Tells the downloader to stop downloads.
     *
     * @param reason an optional reason for being stopped, used to notify the user.
     */
    fun stopDownloads(reason: String? = null) {
        downloader.stop(reason)
    }

    /**
     * Tells the downloader to pause downloads.
     */
    fun pauseDownloads() {
        downloader.pause()
    }

    /**
     * Empties the download queue.
     *
     * @param isNotification value that determines if status is set (needed for view updates)
     */
    fun clearQueue(isNotification: Boolean = false) {
        downloader.clearQueue(isNotification)
    }

    /**
     * Returns the download from queue if the chapter is queued for download
     * else it will return null which means that the chapter is not queued for download
     *
     * @param chapterId the chapter to check.
     */
    fun getQueuedDownloadOrNull(chapterId: Long): Download? {
        return queue.find { it.chapter.id == chapterId }
    }

    fun startDownloadNow(chapterId: Long?) {
        if (chapterId == null) return
        val download = getQueuedDownloadOrNull(chapterId)
        // If not in queue try to start a new download
        val toAdd = download ?: runBlocking { Download.fromChapterId(chapterId) } ?: return
        val queue = queue.toMutableList()
        download?.let { queue.remove(it) }
        queue.add(0, toAdd)
        reorderQueue(queue)
        if (downloader.isPaused()) {
            if (DownloadService.isRunning(context)) {
                downloader.start()
            } else {
                DownloadService.start(context)
            }
        }
    }

    /**
     * Reorders the download queue.
     *
     * @param downloads value to set the download queue to
     */
    fun reorderQueue(downloads: List<Download>) {
        val wasRunning = downloader.isRunning

        if (downloads.isEmpty()) {
            DownloadService.stop(context)
            queue.clear()
            return
        }

        downloader.pause()
        queue.clear()
        queue.addAll(downloads)

        if (wasRunning) {
            downloader.start()
        }
    }

    /**
     * Tells the downloader to enqueue the given list of chapters.
     *
     * @param manga the manga of the chapters.
     * @param chapters the list of chapters to enqueue.
     * @param autoStart whether to start the downloader after enqueing the chapters.
     */
    fun downloadChapters(manga: Manga, chapters: List<Chapter>, autoStart: Boolean = true) {
        downloader.queueChapters(manga, chapters, autoStart)
    }

    /**
     * Tells the downloader to enqueue the given list of downloads at the start of the queue.
     *
     * @param downloads the list of downloads to enqueue.
     */
    fun addDownloadsToStartOfQueue(downloads: List<Download>) {
        if (downloads.isEmpty()) return
        queue.toMutableList().apply {
            addAll(0, downloads)
            reorderQueue(this)
        }
        if (!DownloadService.isRunning(context)) DownloadService.start(context)
    }

    /**
     * Builds the page list of a downloaded chapter.
     *
     * @param source the source of the chapter.
     * @param manga the manga of the chapter.
     * @param chapter the downloaded chapter.
     * @return the list of pages from the chapter.
     */
    fun buildPageList(source: Source, manga: Manga, chapter: Chapter): List<Page> {
        val chapterDir = provider.findChapterDir(chapter.name, chapter.scanlator, manga.title, source)
        val files = chapterDir?.listFiles().orEmpty()
            .filter { "image" in it.type.orEmpty() }

        if (files.isEmpty()) {
            throw Exception(context.getString(R.string.page_list_empty_error))
        }

        return files.sortedBy { it.name }
            .mapIndexed { i, file ->
                Page(i, uri = file.uri).apply { status = Page.State.READY }
            }
    }

    /**
     * Returns true if the chapter is downloaded.
     *
     * @param chapterName the name of the chapter to query.
     * @param chapterScanlator scanlator of the chapter to query
     * @param mangaTitle the title of the manga to query.
     * @param sourceId the id of the source of the chapter.
     * @param skipCache whether to skip the directory cache and check in the filesystem.
     */
    fun isChapterDownloaded(
        chapterName: String,
        chapterScanlator: String?,
        mangaTitle: String,
        sourceId: Long,
        skipCache: Boolean = false,
    ): Boolean {
        return cache.isChapterDownloaded(chapterName, chapterScanlator, mangaTitle, sourceId, skipCache)
    }

    /**
     * Returns the amount of downloaded chapters.
     */
    fun getDownloadCount(): Int {
        return cache.getTotalDownloadCount()
    }

    /**
     * Returns the amount of downloaded chapters for a manga.
     *
     * @param manga the manga to check.
     */
    fun getDownloadCount(manga: Manga): Int {
        return cache.getDownloadCount(manga)
    }

    fun cancelQueuedDownloads(downloads: List<Download>) {
        removeFromDownloadQueue(downloads.map { it.chapter })
    }

    /**
     * Deletes the directories of a list of downloaded chapters.
     *
     * @param chapters the list of chapters to delete.
     * @param manga the manga of the chapters.
     * @param source the source of the chapters.
     */
    fun deleteChapters(chapters: List<Chapter>, manga: Manga, source: Source) {
        val filteredChapters = getChaptersToDelete(chapters, manga)
        if (filteredChapters.isNotEmpty()) {
            launchIO {
                removeFromDownloadQueue(filteredChapters)

                val (mangaDir, chapterDirs) = provider.findChapterDirs(filteredChapters, manga, source)
                chapterDirs.forEach { it.delete() }
                cache.removeChapters(filteredChapters, manga)

                // Delete manga directory if empty
                if (mangaDir?.listFiles()?.isEmpty() == true) {
                    deleteManga(manga, source, removeQueued = false)
                }
            }
        }
    }

    /**
     * Deletes the directory of a downloaded manga.
     *
     * @param manga the manga to delete.
     * @param source the source of the manga.
     * @param removeQueued whether to also remove queued downloads.
     */
    fun deleteManga(manga: Manga, source: Source, removeQueued: Boolean = true) {
        launchIO {
            if (removeQueued) {
                queue.remove(manga)
            }
            provider.findMangaDir(manga.title, source)?.delete()
            cache.removeManga(manga)

            // Delete source directory if empty
            val sourceDir = provider.findSourceDir(source)
            if (sourceDir?.listFiles()?.isEmpty() == true) {
                sourceDir.delete()
                cache.removeSource(source)
            }
        }
    }

    private fun removeFromDownloadQueue(chapters: List<Chapter>) {
        val wasRunning = downloader.isRunning
        if (wasRunning) {
            downloader.pause()
        }

        queue.remove(chapters)

        if (wasRunning) {
            if (queue.isEmpty()) {
                DownloadService.stop(context)
                downloader.stop()
            } else if (queue.isNotEmpty()) {
                downloader.start()
            }
        }
    }

    /**
     * Adds a list of chapters to be deleted later.
     *
     * @param chapters the list of chapters to delete.
     * @param manga the manga of the chapters.
     */
    fun enqueueChaptersToDelete(chapters: List<Chapter>, manga: Manga) {
        pendingDeleter.addChapters(getChaptersToDelete(chapters, manga), manga)
    }

    /**
     * Triggers the execution of the deletion of pending chapters.
     */
    fun deletePendingChapters() {
        val pendingChapters = pendingDeleter.getPendingChapters()
        for ((manga, chapters) in pendingChapters) {
            val source = sourceManager.get(manga.source) ?: continue
            deleteChapters(chapters, manga, source)
        }
    }

    /**
     * Renames source download folder
     *
     * @param oldSource the old source.
     * @param newSource the new source.
     */
    fun renameSource(oldSource: Source, newSource: Source) {
        val oldFolder = provider.findSourceDir(oldSource) ?: return
        val newName = provider.getSourceDirName(newSource)

        val capitalizationChanged = oldFolder.name.equals(newName, ignoreCase = true)
        if (capitalizationChanged) {
            val tempName = newName + "_tmp"
            if (oldFolder.renameTo(tempName).not()) {
                logcat(LogPriority.ERROR) { "Failed to rename source download folder: ${oldFolder.name}" }
                return
            }
        }

        if (oldFolder.renameTo(newName).not()) {
            logcat(LogPriority.ERROR) { "Failed to rename source download folder: ${oldFolder.name}" }
        }
    }

    /**
     * Renames an already downloaded chapter
     *
     * @param source the source of the manga.
     * @param manga the manga of the chapter.
     * @param oldChapter the existing chapter with the old name.
     * @param newChapter the target chapter with the new name.
     */
    fun renameChapter(source: Source, manga: Manga, oldChapter: Chapter, newChapter: Chapter) {
        val oldNames = provider.getValidChapterDirNames(oldChapter.name, oldChapter.scanlator)
        val mangaDir = provider.getMangaDir(manga.title, source)

        // Assume there's only 1 version of the chapter name formats present
        val oldDownload = oldNames.asSequence()
            .mapNotNull { mangaDir.findFile(it) }
            .firstOrNull() ?: return

        var newName = provider.getChapterDirName(newChapter.name, newChapter.scanlator)
        if (oldDownload.isFile && oldDownload.name?.endsWith(".cbz") == true) {
            newName += ".cbz"
        }

        if (oldDownload.renameTo(newName)) {
            cache.removeChapter(oldChapter, manga)
            cache.addChapter(newName, mangaDir, manga)
        } else {
            logcat(LogPriority.ERROR) { "Could not rename downloaded chapter: ${oldNames.joinToString()}" }
        }
    }

    private fun getChaptersToDelete(chapters: List<Chapter>, manga: Manga): List<Chapter> {
        // Retrieve the categories that are set to exclude from being deleted on read
        val categoriesToExclude = downloadPreferences.removeExcludeCategories().get().map(String::toLong)

        val categoriesForManga = runBlocking { getCategories.await(manga.id) }
            .map { it.id }
            .takeUnless { it.isEmpty() }
            ?: listOf(0)

        return if (categoriesForManga.intersect(categoriesToExclude).isNotEmpty()) {
            chapters.filterNot { it.read }
        } else if (!downloadPreferences.removeBookmarkedChapters().get()) {
            chapters.filterNot { it.bookmark }
        } else {
            chapters
        }
    }
}
