package exh.ui.smartsearch

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.ui.base.controller.NucleusController
import eu.kanade.tachiyomi.ui.base.controller.withFadeTransaction
import eu.kanade.tachiyomi.ui.catalogue.CatalogueController
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.util.toast
import exh.util.await
import info.debatty.java.stringsimilarity.NormalizedLevenshtein
import kotlinx.android.synthetic.main.eh_smart_search.*
import kotlinx.coroutines.*
import rx.schedulers.Schedulers
import uy.kohesive.injekt.injectLazy
import kotlin.text.StringBuilder

class SmartSearchController(bundle: Bundle? = null) : NucleusController<SmartSearchPresenter>() {
    private val sourceManager: SourceManager by injectLazy()
    private val db: DatabaseHelper by injectLazy()

    private val source = sourceManager.get(bundle?.getLong(ARG_SOURCE_ID, -1) ?: -1) as? CatalogueSource
    private val smartSearchConfig: CatalogueController.SmartSearchConfig? = bundle?.getParcelable(ARG_SMART_SEARCH_CONFIG)

    override fun inflateView(inflater: LayoutInflater, container: ViewGroup) =
            inflater.inflate(R.layout.eh_smart_search, container, false)!!

    override fun getTitle() = source?.name ?: ""

    override fun createPresenter() = SmartSearchPresenter()

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)

        appbar.bringToFront()

        if(source == null || smartSearchConfig == null) {
            router.popCurrentController()
            applicationContext?.toast("Missing data!")
            return
        }

        // TODO Error handling

        // TODO Use activity scope
        GlobalScope.launch(Dispatchers.IO) {
            val resultManga = initiateSmartSearch(source, smartSearchConfig)
            if(resultManga != null) {
                val localManga = networkToLocalManga(resultManga, source.id)
                val transaction = MangaController(localManga, true).withFadeTransaction()
                withContext(Dispatchers.Main) {
                    router.replaceTopController(transaction)
                }
            } else {
                // TODO Open search
                router.popCurrentController()
            }
            println(resultManga)
        }
    }

    suspend fun initiateSmartSearch(source: CatalogueSource, config: CatalogueController.SmartSearchConfig): SManga? {
        val cleanedTitle = cleanSmartSearchTitle(config.title)

        val queries = getSmartSearchQueries(cleanedTitle)

        val eligibleManga = supervisorScope {
            queries.map { query ->
                async(Dispatchers.IO) {
                    val searchResults = source.fetchSearchManga(1, query, FilterList()).toSingle().await(Schedulers.io())

                    searchResults.mangas.map {
                        val cleanedMangaTitle = cleanSmartSearchTitle(it.title)
                        val normalizedDistance = NormalizedLevenshtein().similarity(cleanedTitle, cleanedMangaTitle)
                        SearchEntry(it, normalizedDistance)
                    }.filter { (_, normalizedDistance) ->
                        normalizedDistance >= MIN_ELIGIBLE_THRESHOLD
                    }
                }
            }.flatMap { it.await() }
        }

        return eligibleManga.maxBy { it.dist }?.manga
    }

    fun getSmartSearchQueries(cleanedTitle: String): List<String> {
        val splitCleanedTitle = cleanedTitle.split(" ")
        val splitSortedByLargest = splitCleanedTitle.sortedByDescending { it.length }

        if(splitCleanedTitle.isEmpty()) {
            return emptyList()
        }

        // Search cleaned title
        // Search two largest words
        // Search largest word
        // Search first two words
        // Search first word

        val searchQueries = listOf(
                listOf(cleanedTitle),
                splitSortedByLargest.take(2),
                splitSortedByLargest.take(1),
                splitCleanedTitle.take(2),
                splitCleanedTitle.take(1)
        )

        return searchQueries.map {
            it.joinToString().trim()
        }.distinct()
    }

    fun cleanSmartSearchTitle(title: String): String {
        val preTitle = title.toLowerCase()

        // Remove text in brackets
        var cleanedTitle = removeTextInBrackets(preTitle, true)
        if(cleanedTitle.length <= 5) { // Title is suspiciously short, try parsing it backwards
            cleanedTitle = removeTextInBrackets(preTitle, false)
        }

        // Strip non-special characters
        cleanedTitle = cleanedTitle.replace(titleRegex, " ")

        // Strip splitters and consecutive spaces
        cleanedTitle = cleanedTitle.trim().replace(" - ", " ").replace(consecutiveSpacesRegex, " ").trim()

        return cleanedTitle
    }

    private fun removeTextInBrackets(text: String, readForward: Boolean): String {
        val bracketPairs = listOf(
                '(' to ')',
                '[' to ']',
                '<' to '>',
                '{' to '}'
        )
        var openingBracketPairs = bracketPairs.mapIndexed { index, (opening, _) ->
            opening to index
        }.toMap()
        var closingBracketPairs = bracketPairs.mapIndexed { index, (_, closing) ->
            closing to index
        }.toMap()

        // Reverse pairs if reading backwards
        if(!readForward) {
            val tmp = openingBracketPairs
            openingBracketPairs = closingBracketPairs
            closingBracketPairs = tmp
        }

        val depthPairs = bracketPairs.map { 0 }.toMutableList()

        val result = StringBuilder()
        for(c in if(readForward) text else text.reversed()) {
            val openingBracketDepthIndex = openingBracketPairs[c]
            if(openingBracketDepthIndex != null) {
                depthPairs[openingBracketDepthIndex]++
            } else {
                val closingBracketDepthIndex = closingBracketPairs[c]
                if(closingBracketDepthIndex != null) {
                    depthPairs[closingBracketDepthIndex]--
                } else {
                    if(depthPairs.all { it <= 0 }) {
                        result.append(c)
                    } else {
                        // In brackets, do not append to result
                    }
                }
            }
        }

        return result.toString()
    }

    /**
     * Returns a manga from the database for the given manga from network. It creates a new entry
     * if the manga is not yet in the database.
     *
     * @param sManga the manga from the source.
     * @return a manga from the database.
     */
    private fun networkToLocalManga(sManga: SManga, sourceId: Long): Manga {
        var localManga = db.getManga(sManga.url, sourceId).executeAsBlocking()
        if (localManga == null) {
            val newManga = Manga.create(sManga.url, sManga.title, sourceId)
            newManga.copyFrom(sManga)
            val result = db.insertManga(newManga).executeAsBlocking()
            newManga.id = result.insertedId()
            localManga = newManga
        }
        return localManga
    }

    data class SearchEntry(val manga: SManga, val dist: Double)

    companion object {
        const val ARG_SOURCE_ID = "SOURCE_ID"
        const val ARG_SMART_SEARCH_CONFIG = "SMART_SEARCH_CONFIG"
        const val MIN_ELIGIBLE_THRESHOLD = 0.7

        private val titleRegex = Regex("[^a-zA-Z0-9- ]")
        private val consecutiveSpacesRegex = Regex(" +")
    }
}