package eu.kanade.tachiyomi.data.track

import android.content.Context
import eu.kanade.tachiyomi.data.track.anilist.Anilist
import eu.kanade.tachiyomi.data.track.bangumi.Bangumi
import eu.kanade.tachiyomi.data.track.kavita.Kavita
import eu.kanade.tachiyomi.data.track.kitsu.Kitsu
import eu.kanade.tachiyomi.data.track.komga.Komga
import eu.kanade.tachiyomi.data.track.mangaupdates.MangaUpdates
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeList
import eu.kanade.tachiyomi.data.track.shikimori.Shikimori
import eu.kanade.tachiyomi.data.track.suwayomi.Suwayomi

class TrackManager(context: Context) {

    companion object {
        const val MYANIMELIST = 1L
        const val ANILIST = 2L
        const val KITSU = 3L
        const val SHIKIMORI = 4L
        const val BANGUMI = 5L
        const val KOMGA = 6L
        const val MANGA_UPDATES = 7L
        const val KAVITA = 8L
        const val SUWAYOMI = 9L
    }

    val myAnimeList = MyAnimeList(MYANIMELIST)
    val aniList = Anilist(ANILIST)
    val kitsu = Kitsu(KITSU)
    val shikimori = Shikimori(SHIKIMORI)
    val bangumi = Bangumi(BANGUMI)
    val komga = Komga(KOMGA)
    val mangaUpdates = MangaUpdates(MANGA_UPDATES)
    val kavita = Kavita(context, KAVITA)
    val suwayomi = Suwayomi(SUWAYOMI)

    val services = listOf(myAnimeList, aniList, kitsu, shikimori, bangumi, komga, mangaUpdates, kavita, suwayomi)

    fun getService(id: Long) = services.find { it.id == id }

    fun hasLoggedServices() = services.any { it.isLoggedIn }
}
