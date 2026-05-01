package eu.kanade.tachiyomi.data.translation

import android.app.Application
import android.graphics.BitmapFactory
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.reader.loader.ChapterLoader
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.domain.chapter.interactor.GetChapter
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.source.model.StubSource
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.ByteArrayInputStream

class TranslationImageResolver(
    private val context: Application = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val downloadProvider: DownloadProvider = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val getChapter: GetChapter = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
) {
    suspend fun getPageCount(mangaId: Long, chapterId: Long): Int {
        return withLoadedChapter(mangaId, chapterId) { chapter, _ ->
            chapter.pages.orEmpty().size
        }
    }

    suspend fun resolvePage(mangaId: Long, chapterId: Long, pageIndex: Int): TranslationPageImage {
        return withLoadedChapter(mangaId, chapterId) { chapter, source ->
            val pages = chapter.pages.orEmpty()
            val page = pages.getOrNull(pageIndex)
                ?: error("Page $pageIndex is outside loaded chapter page range (${pages.size})")
            val bytes = page.stream?.invoke()?.use { it.readBytes() }
                ?: when (source) {
                    is HttpSource -> {
                        if (page.imageUrl.isNullOrBlank()) {
                            page.imageUrl = source.getImageUrl(page)
                        }
                        source.getImage(page).use { response ->
                            response.body.bytes()
                        }
                    }
                    else -> error("Page $pageIndex did not expose an image stream")
                }

            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

            TranslationPageImage(
                bytes = bytes,
                mimeType = ImageUtil.findImageType { ByteArrayInputStream(bytes) }?.mime ?: "image/jpeg",
                sourceImageKey = page.imageUrl ?: page.url.takeIf { it.isNotBlank() } ?: "$chapterId/$pageIndex",
                width = options.outWidth.takeIf { it > 0 },
                height = options.outHeight.takeIf { it > 0 },
            )
        }
    }

    private suspend fun <T> withLoadedChapter(
        mangaId: Long,
        chapterId: Long,
        block: suspend (ReaderChapter, Source) -> T,
    ): T {
        val manga = getManga.await(mangaId) ?: error("Manga $mangaId not found")
        val chapter = getChapter.await(chapterId) ?: error("Chapter $chapterId not found")
        val source = sourceManager.getOrStub(manga.source)
        if (source is StubSource) {
            error("Source ${manga.source} is not installed")
        }
        val readerChapter = ReaderChapter(chapter).also { it.ref() }
        return try {
            ChapterLoader(
                context = context,
                downloadManager = downloadManager,
                downloadProvider = downloadProvider,
                manga = manga,
                source = source,
            ).loadChapter(readerChapter)
            block(readerChapter, source)
        } finally {
            readerChapter.unref()
        }
    }
}

data class TranslationPageImage(
    val bytes: ByteArray,
    val mimeType: String,
    val sourceImageKey: String,
    val width: Int?,
    val height: Int?,
)
