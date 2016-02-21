package eu.kanade.tachiyomi.data.mangasync

import android.content.Context
import eu.kanade.tachiyomi.data.mangasync.base.MangaSyncService
import eu.kanade.tachiyomi.data.mangasync.services.MyAnimeList

class MangaSyncManager(private val context: Context) {

    val services: List<MangaSyncService>
    val myAnimeList: MyAnimeList

    companion object {
        const val MYANIMELIST = 1
    }

    init {
        myAnimeList = MyAnimeList(context, MYANIMELIST)
        services = listOf(myAnimeList)
    }

    fun getService(id: Int): MangaSyncService = services.find { it.id == id }!!

}
