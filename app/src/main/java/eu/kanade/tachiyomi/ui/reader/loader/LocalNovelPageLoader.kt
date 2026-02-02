package eu.kanade.tachiyomi.ui.reader.loader

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.NovelSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.util.TextSplitter
import tachiyomi.core.common.util.lang.withIOContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Loader used to load pages for a local novel source.
 */
class LocalNovelPageLoader(
    private val chapter: ReaderChapter,
    private val source: Source,
    private val readerPreferences: ReaderPreferences = Injekt.get(),
) : PageLoader() {

    override var isLocal: Boolean = true

    override suspend fun getPages(): List<ReaderPage> {
        val pages = (source as? CatalogueSource)?.getPageList(chapter.chapter)
            ?: listOf(Page(0, chapter.chapter.url))

        return pages.mapIndexed { index, page ->
            ReaderPage(index, page.url, page.imageUrl)
        }
    }

    override suspend fun loadPage(page: ReaderPage) = withIOContext {
        if (page.status == Page.State.Ready) return@withIOContext

        page.status = Page.State.LoadPage
        try {
            if (source is NovelSource) {
                var text = source.fetchPageText(Page(page.index, page.url, page.imageUrl))
                
                // Apply auto-split if enabled
                if (readerPreferences.novelAutoSplitText().get()) {
                    val wordCount = readerPreferences.novelAutoSplitWordCount().get()
                    if (wordCount > 0) {
                        text = TextSplitter.splitText(text, wordCount)
                    }
                }
                
                page.text = text
                page.status = Page.State.Ready
            } else {
                throw IllegalStateException("Source is not a NovelSource")
            }
        } catch (e: Throwable) {
            page.status = Page.State.Error(e)
        }
    }
}
