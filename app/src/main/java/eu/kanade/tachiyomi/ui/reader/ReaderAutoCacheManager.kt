package eu.kanade.tachiyomi.ui.reader

import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class ReaderAutoCacheManager(
    private val scope: CoroutineScope,
    private val prepareChapter: suspend (ReaderChapter) -> Unit,
    private val canCacheChapter: (ReaderChapter) -> Boolean,
    private val cacheChapterPages: suspend (ReaderChapter) -> Unit,
) {

    private var job: Job? = null

    fun update(enabled: Boolean, viewerChapters: ViewerChapters?) {
        job?.cancel()

        if (!enabled || viewerChapters == null) {
            job = null
            return
        }

        job = scope.launch {
            listOfNotNull(viewerChapters.currChapter, viewerChapters.nextChapter)
                .forEach { chapter ->
                    prepareChapter(chapter)
                    if (canCacheChapter(chapter)) {
                        cacheChapterPages(chapter)
                    }
                }
        }
    }
}
