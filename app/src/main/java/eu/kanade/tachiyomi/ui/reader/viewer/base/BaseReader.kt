package eu.kanade.tachiyomi.ui.reader.viewer.base

import com.davemorrissey.labs.subscaleview.decoder.*
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.data.source.model.Page
import eu.kanade.tachiyomi.ui.base.fragment.BaseFragment
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import java.util.*

/**
 * Base reader containing the common data that can be used by its implementations. It does not
 * contain any UI related action.
 */
abstract class BaseReader : BaseFragment() {

    companion object {
        /**
         * Rapid decoder.
         */
        const val RAPID_DECODER = 0

        /**
         * Skia decoder.
         */
        const val SKIA_DECODER = 1
    }

    /**
     * List of chapters added in the reader.
     */
    private var chapters = ArrayList<Chapter>()

    /**
     * List of pages added in the reader. It can contain pages from more than one chapter.
     */
    var pages: MutableList<Page> = ArrayList()
        private set

    /**
     * Current visible position of [pages].
     */
    var currentPage: Int = 0
        protected set

    /**
     * Region decoder class to use.
     */
    lateinit var regionDecoderClass: Class<out ImageRegionDecoder>
        private set

    /**
     * Bitmap decoder class to use.
     */
    lateinit var bitmapDecoderClass: Class<out ImageDecoder>
        private set

    /**
     * Whether tap navigation is enabled or not.
     */
    val tappingEnabled by lazy { readerActivity.preferences.readWithTapping().getOrDefault() }

    /**
     * Whether the reader has requested to append a chapter. Used with seamless mode to avoid
     * restarting requests when changing pages.
     */
    private var hasRequestedNextChapter: Boolean = false

    /**
     * Updates the reader activity with the active page.
     */
    fun updatePageNumber() {
        val activePage = getActivePage()
        readerActivity.onPageChanged(activePage.pageNumber, activePage.chapter.pages.size)
    }

    /**
     * Returns the active page.
     */
    fun getActivePage(): Page {
        return pages[currentPage]
    }

    /**
     * Called when a page changes. Implementations must call this method.
     *
     * @param position the new current page.
     */
    fun onPageChanged(position: Int) {
        val oldPage = pages[currentPage]
        val newPage = pages[position]
        newPage.chapter.last_page_read = newPage.pageNumber

        if (readerActivity.presenter.isSeamlessMode) {
            val oldChapter = oldPage.chapter
            val newChapter = newPage.chapter
            if (!hasRequestedNextChapter && position > pages.size - 5) {
                hasRequestedNextChapter = true
                readerActivity.presenter.appendNextChapter()
            }
            if (oldChapter.id != newChapter.id) {
                // Active chapter has changed.
                readerActivity.onEnterChapter(newPage.chapter, newPage.pageNumber)
            }
        }
        currentPage = position
        updatePageNumber()
    }

    /**
     * Sets the active page.
     *
     * @param page the page to display.
     */
    fun setActivePage(page: Page) {
        setActivePage(getPageIndex(page))
    }

    /**
     * Searchs for the index of a page in the current list without requiring them to be the same
     * object.
     *
     * @param search the page to search.
     * @return the index of the page in [pages] or 0 if it's not found.
     */
    fun getPageIndex(search: Page): Int {
        for ((index, page) in pages.withIndex()) {
            if (page.pageNumber == search.pageNumber && page.chapter.id == search.chapter.id) {
                return index
            }
        }
        return 0
    }

    /**
     * Called from the presenter when the page list of a chapter is ready. This method is called
     * on every [onResume], so we add some logic to avoid duplicating chapters.
     *
     * @param chapter the chapter to set.
     * @param currentPage the initial page to display.
     */
    fun onPageListReady(chapter: Chapter, currentPage: Page) {
        if (!chapters.contains(chapter)) {
            // if we reset the loaded page we also need to reset the loaded chapters
            chapters = ArrayList<Chapter>()
            chapters.add(chapter)
            pages = ArrayList(chapter.pages)
            onChapterSet(chapter, currentPage)
        } else {
            setActivePage(currentPage)
        }
    }

    /**
     * Called from the presenter when the page list of a chapter to append is ready. This method is
     * called on every [onResume], so we add some logic to avoid duplicating chapters.
     *
     * @param chapter the chapter to append.
     */
    fun onPageListAppendReady(chapter: Chapter) {
        if (!chapters.contains(chapter)) {
            hasRequestedNextChapter = false
            chapters.add(chapter)
            pages.addAll(chapter.pages)
            onChapterAppended(chapter)
        }
    }

    /**
     * Sets the active page.
     *
     * @param pageNumber the index of the page from [pages].
     */
    abstract fun setActivePage(pageNumber: Int)

    /**
     * Called when a new chapter is set in [BaseReader].
     *
     * @param chapter the chapter set.
     * @param currentPage the initial page to display.
     */
    abstract fun onChapterSet(chapter: Chapter, currentPage: Page)

    /**
     * Called when a chapter is appended in [BaseReader].
     *
     * @param chapter the chapter appended.
     */
    abstract fun onChapterAppended(chapter: Chapter)

    /**
     * Moves pages forward. Implementations decide how to move (by a page, by some distance...).
     */
    abstract fun moveToNext()

    /**
     * Moves pages backward. Implementations decide how to move (by a page, by some distance...).
     */
    abstract fun moveToPrevious()

    /**
     * Sets the active decoder class.
     *
     * @param value the decoder class to use.
     */
    fun setDecoderClass(value: Int) {
        when (value) {
            RAPID_DECODER -> {
                // Using Skia because Rapid isn't stable. Rapid is still used for region decoding.
                // https://github.com/inorichi/tachiyomi/issues/97
                //bitmapDecoderClass = RapidImageDecoder.class;
                regionDecoderClass = RapidImageRegionDecoder::class.java
                bitmapDecoderClass = SkiaImageDecoder::class.java
            }
            SKIA_DECODER -> {
                regionDecoderClass = SkiaImageRegionDecoder::class.java
                bitmapDecoderClass = SkiaImageDecoder::class.java
            }
        }
    }

    /**
     * Property to get the reader activity.
     */
    val readerActivity: ReaderActivity
        get() = activity as ReaderActivity

}
