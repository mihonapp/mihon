package eu.kanade.tachiyomi.ui.manga

import android.content.Context
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.ui.manga.chapter.MangaAllInOneChapterItem
import eu.kanade.tachiyomi.util.system.getResourceColor
import java.text.DateFormat
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Date
import kotlinx.coroutines.CoroutineScope
import uy.kohesive.injekt.injectLazy

class MangaAllInOneAdapter(
    controller: MangaAllInOneController,
    context: Context
) : FlexibleAdapter<IFlexible<*>>(null, controller, true) {

    val delegate: MangaAllInOneInterface = controller

    val preferences: PreferencesHelper by injectLazy()

    var items: List<MangaAllInOneChapterItem> = emptyList()

    val readColor = context.getResourceColor(R.attr.colorOnSurface, 0.38f)
    val unreadColor = context.getResourceColor(R.attr.colorOnSurface)

    val bookmarkedColor = context.getResourceColor(R.attr.colorAccent)

    val decimalFormat = DecimalFormat(
        "#.###",
        DecimalFormatSymbols()
            .apply { decimalSeparator = '.' }
    )

    val dateFormat: DateFormat = preferences.dateFormat()

    override fun updateDataSet(items: List<IFlexible<*>>?) {
        this.items = items as List<MangaAllInOneChapterItem>? ?: emptyList()
        super.updateDataSet(items)
    }

    fun indexOf(item: MangaAllInOneChapterItem): Int {
        return items.indexOf(item)
    }

    interface MangaAllInOneInterface : MangaHeaderInterface

    interface MangaHeaderInterface {
        fun openSmartSearch()
        fun mangaPresenter(): MangaAllInOnePresenter
        fun openRecommends()
        fun onNextManga(manga: Manga, source: Source, chapters: List<MangaAllInOneChapterItem>, lastUpdateDate: Date, chapterCount: Float)
        fun setMangaInfo(manga: Manga, source: Source?, chapters: List<MangaAllInOneChapterItem>, lastUpdateDate: Date, chapterCount: Float)
        fun openInWebView()
        fun shareManga()
        fun fetchMangaFromSource(manualFetch: Boolean = false, fetchManga: Boolean = true, fetchChapters: Boolean = true)
        fun onFetchMangaDone()
        fun onFetchMangaError(error: Throwable)
        fun setRefreshing(value: Boolean)
        fun onFavoriteClick()
        fun onCategoriesClick()
        fun updateCategoriesForMangas(mangas: List<Manga>, categories: List<Category>)
        fun performGlobalSearch(query: String)
        fun wrapTag(namespace: String, tag: String): String
        fun isEHentaiBasedSource(): Boolean
        fun performSearch(query: String)
        fun openTracking()
        suspend fun mergeWithAnother()
        fun copyToClipboard(label: String, text: String)
        fun migrateManga()
        fun isInitialLoadAndFromSource(): Boolean
        fun removeInitialLoad()
        val controllerScope: CoroutineScope
    }
}
