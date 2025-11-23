package eu.kanade.tachiyomi.data.track

import eu.kanade.tachiyomi.data.track.anilist.Anilist
import eu.kanade.tachiyomi.data.track.bangumi.Bangumi
import eu.kanade.tachiyomi.data.track.kavita.Kavita
import eu.kanade.tachiyomi.data.track.kitsu.Kitsu
import eu.kanade.tachiyomi.data.track.komga.Komga
import eu.kanade.tachiyomi.data.track.mangaupdates.MangaUpdates
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeList
import eu.kanade.tachiyomi.data.track.shikimori.Shikimori
import eu.kanade.tachiyomi.data.track.suwayomi.Suwayomi
import kotlinx.coroutines.flow.combine
import mihonx.auth.Auth
import kotlin.io.encoding.Base64
import kotlin.random.Random

class TrackerManager {

    companion object {
        const val ANILIST = 2L
        const val KITSU = 3L
        const val KAVITA = 8L
    }

    private val oAuthStates = mutableMapOf<Long, String>()

    val myAnimeList = MyAnimeList(1L)
    val aniList = Anilist(ANILIST)
    val kitsu = Kitsu(KITSU)
    val shikimori = Shikimori(4L)
    val bangumi = Bangumi(5L)
    val komga = Komga(6L)
    val mangaUpdates = MangaUpdates(7L)
    val kavita = Kavita(KAVITA)
    val suwayomi = Suwayomi(9L)

    val trackers = listOf(myAnimeList, aniList, kitsu, shikimori, bangumi, komga, mangaUpdates, kavita, suwayomi)

    fun loggedInTrackers() = trackers.filter { it.isLoggedIn }

    fun loggedInTrackersFlow() = combine(trackers.map { it.isLoggedInFlow }) {
        it.mapIndexedNotNull { index, isLoggedIn ->
            if (isLoggedIn) trackers[index] else null
        }
    }

    fun get(id: Long) = trackers.find { it.id == id }

    fun getAll(ids: Set<Long>) = trackers.filter { it.id in ids }

    fun getOAuthUrl(id: Long, tracker: Auth.OAuth): String {
        val random = Base64.UrlSafe.encode(Random.nextBytes(9))
        oAuthStates[id] = random
        return tracker.getOAuthUrl(random)
    }

    suspend fun onOAuthCallback(data: Map<String, String>): Boolean {
        val state = data["state"] ?: return false
        val trackerIdAndState = oAuthStates.entries.firstOrNull { it.value == state } ?: return false
        oAuthStates.remove(trackerIdAndState.key)
        val tracker = trackers.firstOrNull { it is Auth.OAuth && it.id == trackerIdAndState.key } ?: return false
        return (tracker as Auth.OAuth).onOAuthCallback(data)
    }
}
