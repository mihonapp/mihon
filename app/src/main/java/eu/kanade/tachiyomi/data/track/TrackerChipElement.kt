package eu.kanade.tachiyomi.data.track

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Public
import androidx.compose.ui.graphics.vector.ImageVector
import eu.kanade.tachiyomi.data.track.anilist.Anilist
import eu.kanade.tachiyomi.data.track.anilist.AnilistApi
import eu.kanade.tachiyomi.data.track.bangumi.Bangumi
import eu.kanade.tachiyomi.data.track.kitsu.Kitsu
import eu.kanade.tachiyomi.data.track.kitsu.KitsuApi
import eu.kanade.tachiyomi.data.track.mangaupdates.MangaUpdates
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeList
import eu.kanade.tachiyomi.data.track.shikimori.Shikimori
import eu.kanade.tachiyomi.data.track.shikimori.ShikimoriApi
import eu.kanade.tachiyomi.ui.manga.track.TrackItem
import tachiyomi.presentation.core.icons.Anilist
import tachiyomi.presentation.core.icons.Bangumi
import tachiyomi.presentation.core.icons.CustomIcons
import tachiyomi.presentation.core.icons.Kitsu
import tachiyomi.presentation.core.icons.MangaUpdates
import tachiyomi.presentation.core.icons.MyAnimeListStretched
import tachiyomi.presentation.core.icons.Shikimori
import java.net.URL

class TrackerChipElement(
    val url: String,
    trackItems: List<TrackItem>,
) {
    val hostName: String = URL(url).host ?: "Could not detect hostname"

    val trackerName: String = when (hostName) {
        URL(AnilistApi.baseUrl).host -> Anilist.NAME
        URL(KitsuApi.baseUrl).host -> Kitsu.NAME
        URL(MY_ANIME_LIST_BASE_URL).host -> MyAnimeList.NAME
        URL(ShikimoriApi.baseUrl).host -> Shikimori.NAME
        in BANGUMI_BASE_URLs.map { URL(it).host } -> Bangumi.NAME
        URL(MANGA_UPDATES_BASE_URL).host -> MangaUpdates.NAME
        else -> URL(url).host
    }

    val trackItem: TrackItem? = trackItems.find { trackerName == it.tracker.name }

    val icon: ImageVector = when (trackerName) {
        Anilist.NAME -> CustomIcons.Anilist
        Kitsu.NAME -> CustomIcons.Kitsu
        MyAnimeList.NAME -> CustomIcons.MyAnimeListStretched
        Shikimori.NAME -> CustomIcons.Shikimori
        Bangumi.NAME -> CustomIcons.Bangumi
        MangaUpdates.NAME -> CustomIcons.MangaUpdates
        else -> Icons.Outlined.Public
    }

    val serviceId = when (trackerName) {
        Anilist.NAME -> TrackerManager.ANILIST
        Kitsu.NAME -> TrackerManager.KITSU
        MyAnimeList.NAME -> TrackerManager.MY_ANIME_LIST
        Shikimori.NAME -> TrackerManager.SHIKIMORI
        Bangumi.NAME -> TrackerManager.BANGUMI
        MangaUpdates.NAME -> TrackerManager.MANGA_UPDATES
        else -> null
    }

    val mangaId = when (trackerName) {
        Anilist.NAME -> ANILIST_ID_REGEX.find(URL(url).path)?.groups?.get(1)?.value?.toLong()
        MyAnimeList.NAME -> MY_ANIME_LIST_ID_REGEX.find(URL(url).path)?.groups?.get(1)?.value?.toLong()
        Shikimori.NAME -> SHIKIMORI_ID_REGEX.find(URL(url).path)?.groups?.get(1)?.value?.toLong()
        Bangumi.NAME -> BANGUMI_ID_REGEX.find(URL(url).path)?.groups?.get(1)?.value?.toLong()
        else -> null
    }

    val searchQuery = when (trackerName) {
        Kitsu.NAME -> KITSU_QUERY_REGEX.find(URL(url).path)?.groups?.get(1)?.value
        MangaUpdates.NAME -> MANGA_UPDATES_QUERY_REGEX.find(URL(url).path)?.groups?.get(1)?.value
        else -> null
    }

    val potentiallyUnsafeUrl = when (serviceId) {
        null -> true
        else -> false
    }

    companion object {
        private const val MY_ANIME_LIST_BASE_URL = "https://myanimelist.net"
        private const val MANGA_UPDATES_BASE_URL = "https://www.mangaupdates.com"
        private val BANGUMI_BASE_URLs = listOf("https://bangumi.tv", "https://bgm.tv")

        private val ANILIST_ID_REGEX = Regex("""^/manga/(\d+)""")
        private val MY_ANIME_LIST_ID_REGEX = Regex("""^/manga/(\d+)""")
        private val SHIKIMORI_ID_REGEX = Regex("""^/mangas/(\d+)""")
        private val BANGUMI_ID_REGEX = Regex("""^/subject/(\d+)""")

        private val KITSU_QUERY_REGEX = Regex("""^/manga/(.+)""")
        private val MANGA_UPDATES_QUERY_REGEX = Regex("""^/series/\w+/(.+)""")
    }
}
