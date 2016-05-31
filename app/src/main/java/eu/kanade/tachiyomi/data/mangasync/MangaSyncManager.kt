package eu.kanade.tachiyomi.data.mangasync

import android.content.Context
import eu.kanade.tachiyomi.data.mangasync.myanimelist.MyAnimeList

class MangaSyncManager(private val context: Context) {

    companion object {
        const val MYANIMELIST = 1
    }

    val myAnimeList = MyAnimeList(context, MYANIMELIST)

    val services = listOf(myAnimeList)

    fun getService(id: Int) = services.find { it.id == id }

}
