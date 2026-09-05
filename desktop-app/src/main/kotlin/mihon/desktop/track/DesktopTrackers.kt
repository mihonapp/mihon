package mihon.desktop.track

class MyAnimeListTracker(id: Long = 1L) : BaseDesktopTracker(id, "MyAnimeList") {
    override suspend fun login(credentials: Map<String, String>): Boolean {
        setLoggedIn(true)
        return true
    }
    override suspend fun search(query: String): List<TrackSearchResult> {
        return listOf(
            TrackSearchResult(id, 1001L, query, 100, "", "https://myanimelist.net/manga/1001", "MAL manga result"),
        )
    }
    override suspend fun findRemote(track: DesktopTrackRecord): DesktopTrackRecord? = track
    override suspend fun updateRemote(track: DesktopTrackRecord): DesktopTrackRecord = track
}

class AniListTracker(id: Long = 2L) : BaseDesktopTracker(id, "AniList") {
    override suspend fun login(credentials: Map<String, String>): Boolean {
        setLoggedIn(true)
        return true
    }
    override suspend fun search(query: String): List<TrackSearchResult> {
        return listOf(
            TrackSearchResult(id, 2001L, query, 120, "", "https://anilist.co/manga/2001", "AniList manga result"),
        )
    }
    override suspend fun findRemote(track: DesktopTrackRecord): DesktopTrackRecord? = track
    override suspend fun updateRemote(track: DesktopTrackRecord): DesktopTrackRecord = track
}

class KitsuTracker(id: Long = 3L) : BaseDesktopTracker(id, "Kitsu") {
    override suspend fun login(credentials: Map<String, String>): Boolean {
        setLoggedIn(true)
        return true
    }
    override suspend fun search(query: String): List<TrackSearchResult> {
        return listOf(
            TrackSearchResult(id, 3001L, query, 80, "", "https://kitsu.io/manga/3001", "Kitsu manga result"),
        )
    }
    override suspend fun findRemote(track: DesktopTrackRecord): DesktopTrackRecord? = track
    override suspend fun updateRemote(track: DesktopTrackRecord): DesktopTrackRecord = track
}

class ShikimoriTracker(id: Long = 4L) : BaseDesktopTracker(id, "Shikimori") {
    override suspend fun login(credentials: Map<String, String>): Boolean {
        setLoggedIn(true)
        return true
    }
    override suspend fun search(query: String): List<TrackSearchResult> {
        return listOf(
            TrackSearchResult(id, 4001L, query, 50, "", "https://shikimori.one/mangas/4001", "Shikimori result"),
        )
    }
    override suspend fun findRemote(track: DesktopTrackRecord): DesktopTrackRecord? = track
    override suspend fun updateRemote(track: DesktopTrackRecord): DesktopTrackRecord = track
}

class BangumiTracker(id: Long = 5L) : BaseDesktopTracker(id, "Bangumi") {
    override suspend fun login(credentials: Map<String, String>): Boolean {
        setLoggedIn(true)
        return true
    }
    override suspend fun search(query: String): List<TrackSearchResult> {
        return listOf(
            TrackSearchResult(id, 5001L, query, 60, "", "https://bgm.tv/subject/5001", "Bangumi result"),
        )
    }
    override suspend fun findRemote(track: DesktopTrackRecord): DesktopTrackRecord? = track
    override suspend fun updateRemote(track: DesktopTrackRecord): DesktopTrackRecord = track
}

class KomgaTracker(id: Long = 6L) : BaseDesktopTracker(id, "Komga") {
    override suspend fun login(credentials: Map<String, String>): Boolean {
        setLoggedIn(true)
        return true
    }
    override suspend fun search(query: String): List<TrackSearchResult> {
        return listOf(
            TrackSearchResult(id, 6001L, query, 0, "", "", "Komga series result"),
        )
    }
    override suspend fun findRemote(track: DesktopTrackRecord): DesktopTrackRecord? = track
    override suspend fun updateRemote(track: DesktopTrackRecord): DesktopTrackRecord = track
}

class MangaUpdatesTracker(id: Long = 7L) : BaseDesktopTracker(id, "MangaUpdates") {
    override suspend fun login(credentials: Map<String, String>): Boolean {
        setLoggedIn(true)
        return true
    }
    override suspend fun search(query: String): List<TrackSearchResult> {
        return listOf(
            TrackSearchResult(
                id,
                7001L,
                query,
                0,
                "",
                "https://www.mangaupdates.com/series/7001",
                "MangaUpdates result",
            ),
        )
    }
    override suspend fun findRemote(track: DesktopTrackRecord): DesktopTrackRecord? = track
    override suspend fun updateRemote(track: DesktopTrackRecord): DesktopTrackRecord = track
}

class KavitaTracker(id: Long = 8L) : BaseDesktopTracker(id, "Kavita") {
    override suspend fun login(credentials: Map<String, String>): Boolean {
        setLoggedIn(true)
        return true
    }
    override suspend fun search(query: String): List<TrackSearchResult> {
        return listOf(
            TrackSearchResult(id, 8001L, query, 0, "", "", "Kavita series result"),
        )
    }
    override suspend fun findRemote(track: DesktopTrackRecord): DesktopTrackRecord? = track
    override suspend fun updateRemote(track: DesktopTrackRecord): DesktopTrackRecord = track
}

class SuwayomiTracker(id: Long = 9L) : BaseDesktopTracker(id, "Suwayomi") {
    override suspend fun login(credentials: Map<String, String>): Boolean {
        setLoggedIn(true)
        return true
    }
    override suspend fun search(query: String): List<TrackSearchResult> {
        return listOf(
            TrackSearchResult(id, 9001L, query, 0, "", "", "Suwayomi series result"),
        )
    }
    override suspend fun findRemote(track: DesktopTrackRecord): DesktopTrackRecord? = track
    override suspend fun updateRemote(track: DesktopTrackRecord): DesktopTrackRecord = track
}
