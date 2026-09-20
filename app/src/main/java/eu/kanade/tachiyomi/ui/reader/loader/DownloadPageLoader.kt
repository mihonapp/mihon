package eu.kanade.tachiyomi.ui.reader.loader

import android.content.Context
import android.net.Uri
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.database.models.toDomainChapter
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import mihon.core.archive.archiveReader
import tachiyomi.domain.manga.model.Manga
import uy.kohesive.injekt.injectLazy

/**
 * Loader used to load a chapter from the downloaded chapters.
 */
internal class DownloadPageLoader(
    private val chapter: ReaderChapter,
    private val manga: Manga,
    private val source: Source,
    private val downloadManager: DownloadManager,
    private val downloadProvider: DownloadProvider,
) : PageLoader() {

    private val context: Context by injectLazy()

    private var archivePageLoader: ArchivePageLoader? = null

    override var isLocal: Boolean = true

    override suspend fun getPages(): List<ReaderPage> {
        val dbChapter = chapter.chapter
        val chapterPath = downloadProvider.findChapterDir(
            dbChapter.name,
            dbChapter.scanlator,
            dbChapter.url,
            manga.title,
            source,
        )
        return if (chapterPath?.isFile == true) {
            getPagesFromArchive(chapterPath)
        } else {
            getPagesFromDirectory()
        }
    }

    override fun recycle() {
        super.recycle()
        archivePageLoader?.recycle()
    }

    private suspend fun getPagesFromArchive(file: UniFile): List<ReaderPage> {
        val loader = ArchivePageLoader(file.archiveReader(context)).also { archivePageLoader = it }
        return loader.getPages()
    }

    private fun getPagesFromDirectory(): List<ReaderPage> {
        val appGraph = context.applicationContext as? mihon.app.di.AppGraphProvider
        val translationPreferences = appGraph?.appGraph?.translationPreferences
        val translationManager = appGraph?.appGraph?.translationManager

        val showTranslation = translationPreferences?.showTranslationInReader?.get() ?: false
        val domainChapter = chapter.chapter.toDomainChapter()!!

        if (showTranslation && translationManager != null) {
            val transDir = translationManager.findTranslationChapterDir(source, manga, domainChapter)
            val files = transDir?.listFiles()?.filter {
                it.isFile && (it.name?.endsWith(".jpg", true) == true || it.name?.endsWith(".png", true) == true || it.name?.endsWith(".webp", true) == true)
            }?.sortedBy { it.name }

            if (!files.isNullOrEmpty()) {
                return files.mapIndexed { index, file ->
                    ReaderPage(index, "", "") {
                        file.openInputStream()
                    }.apply {
                        status = Page.State.Ready
                    }
                }
            }
        }

        val pages = downloadManager.buildPageList(source, manga, domainChapter)
        return pages.map { page ->
            ReaderPage(page.index, page.url, page.imageUrl) {
                context.contentResolver.openInputStream(page.uri ?: Uri.EMPTY)!!
            }.apply {
                status = Page.State.Ready
            }
        }
    }

    override suspend fun loadPage(page: ReaderPage) {
        archivePageLoader?.loadPage(page)
    }
}
