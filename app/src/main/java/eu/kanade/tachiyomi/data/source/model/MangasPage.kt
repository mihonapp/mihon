package eu.kanade.tachiyomi.data.source.model

import eu.kanade.tachiyomi.data.database.models.Manga

class MangasPage(val page: Int) {

    val mangas: MutableList<Manga> = mutableListOf()

    lateinit var url: String

    var nextPageUrl: String? = null

}
