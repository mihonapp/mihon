package eu.kanade.tachiyomi.ui.reader.loader.inteceptor

import eu.kanade.tachiyomi.ui.reader.model.ReaderPage

/**
 * An abstract class for intercepting and altering page data before it gets rendered.
 */
abstract class PageLoaderInterceptor(protected val outputPages: List<ReaderPage>) {
    /**
     * Called when the page is first loaded, and whenever its status changes.
     *
     * Do not modify [interceptedPage] directly. Instead, modify
     * [interceptedPage.output][ReaderPage.output] to alter the page's state.
     */
    open suspend fun onStatus(interceptedPage: ReaderPage) {
        interceptedPage.output.apply {
            stream = interceptedPage.stream
            status = interceptedPage.status
        }
    }

    /**
     * Called when the page is first loaded, and whenever its progress changes.
     *
     * Do not modify [interceptedPage] directly. Instead, modify
     * [interceptedPage.output][ReaderPage.output] to alter the page's state.
     */
    open suspend fun onProgress(interceptedPage: ReaderPage) {
        interceptedPage.output.apply {
            progress = interceptedPage.progress
        }
    }

    /**
     * The [ReaderPage] which will be seen by the next consumer down the interceptor chain.
     *
     * This property should only be used on pages owned by the current interceptor.
     */
    protected val ReaderPage.output: ReaderPage
        inline get() = outputPages[index]
}
