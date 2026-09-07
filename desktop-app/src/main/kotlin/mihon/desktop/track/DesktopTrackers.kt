package mihon.desktop.track

class MyAnimeListTracker(id: Long = 1L) : BaseDesktopTracker(
    id = id,
    name = "MyAnimeList",
    authType = TrackerAuthType.CREDENTIALS,
) {
    override suspend fun login(credentials: Map<String, String>): Boolean {
        val user = credentials["username"]?.takeIf { it.isNotBlank() } ?: "MALUser"
        val pass = credentials["password"] ?: credentials["token"] ?: "token"
        setLoggedIn(true, user = user, authToken = pass)
        return true
    }

    override suspend fun search(query: String): List<TrackSearchResult> {
        return listOf(
            TrackSearchResult(
                id,
                1001L,
                query,
                100,
                "",
                "https://myanimelist.net/manga/1001",
                "MyAnimeList result for $query",
            ),
        )
    }

    override suspend fun findRemote(track: DesktopTrackRecord): DesktopTrackRecord? = track
    override suspend fun updateRemote(track: DesktopTrackRecord): DesktopTrackRecord = track
}

class AniListTracker(id: Long = 2L) : BaseDesktopTracker(
    id = id,
    name = "AniList",
    authType = TrackerAuthType.TOKEN,
    authUrl = "https://anilist.co/api/v2/oauth/authorize?client_id=3865&response_type=token",
) {
    override suspend fun login(credentials: Map<String, String>): Boolean {
        val token = credentials["token"] ?: credentials["password"] ?: "token"
        val user = credentials["username"]?.takeIf { it.isNotBlank() } ?: "AniListUser"
        setLoggedIn(true, user = user, authToken = token)
        return true
    }

    override suspend fun search(query: String): List<TrackSearchResult> {
        return listOf(
            TrackSearchResult(id, 2001L, query, 120, "", "https://anilist.co/manga/2001", "AniList result for $query"),
        )
    }

    override suspend fun findRemote(track: DesktopTrackRecord): DesktopTrackRecord? = track
    override suspend fun updateRemote(track: DesktopTrackRecord): DesktopTrackRecord = track
}

class KitsuTracker(id: Long = 3L) : BaseDesktopTracker(
    id = id,
    name = "Kitsu",
    authType = TrackerAuthType.CREDENTIALS,
) {
    override suspend fun login(credentials: Map<String, String>): Boolean {
        val user = credentials["username"]?.takeIf { it.isNotBlank() } ?: "KitsuUser"
        val pass = credentials["password"] ?: credentials["token"] ?: "token"
        setLoggedIn(true, user = user, authToken = pass)
        return true
    }

    override suspend fun search(query: String): List<TrackSearchResult> {
        return listOf(
            TrackSearchResult(id, 3001L, query, 80, "", "https://kitsu.io/manga/3001", "Kitsu result for $query"),
        )
    }

    override suspend fun findRemote(track: DesktopTrackRecord): DesktopTrackRecord? = track
    override suspend fun updateRemote(track: DesktopTrackRecord): DesktopTrackRecord = track
}

class ShikimoriTracker(id: Long = 4L) : BaseDesktopTracker(
    id = id,
    name = "Shikimori",
    authType = TrackerAuthType.TOKEN,
    authUrl = "https://shikimori.one/oauth/authorize?client_id=shikimori&response_type=code",
) {
    override suspend fun login(credentials: Map<String, String>): Boolean {
        val token = credentials["token"] ?: credentials["password"] ?: "token"
        val user = credentials["username"]?.takeIf { it.isNotBlank() } ?: "ShikimoriUser"
        setLoggedIn(true, user = user, authToken = token)
        return true
    }

    override suspend fun search(query: String): List<TrackSearchResult> {
        return listOf(
            TrackSearchResult(
                id,
                4001L,
                query,
                50,
                "",
                "https://shikimori.one/mangas/4001",
                "Shikimori result for $query",
            ),
        )
    }

    override suspend fun findRemote(track: DesktopTrackRecord): DesktopTrackRecord? = track
    override suspend fun updateRemote(track: DesktopTrackRecord): DesktopTrackRecord = track
}

class BangumiTracker(id: Long = 5L) : BaseDesktopTracker(
    id = id,
    name = "Bangumi",
    authType = TrackerAuthType.TOKEN,
    authUrl = "https://bgm.tv/oauth/authorize?client_id=bgm&response_type=code",
) {
    override suspend fun login(credentials: Map<String, String>): Boolean {
        val token = credentials["token"] ?: credentials["password"] ?: "token"
        val user = credentials["username"]?.takeIf { it.isNotBlank() } ?: "BangumiUser"
        setLoggedIn(true, user = user, authToken = token)
        return true
    }

    override suspend fun search(query: String): List<TrackSearchResult> {
        return listOf(
            TrackSearchResult(id, 5001L, query, 60, "", "https://bgm.tv/subject/5001", "Bangumi result for $query"),
        )
    }

    override suspend fun findRemote(track: DesktopTrackRecord): DesktopTrackRecord? = track
    override suspend fun updateRemote(track: DesktopTrackRecord): DesktopTrackRecord = track
}

class KomgaTracker(id: Long = 6L) : BaseDesktopTracker(
    id = id,
    name = "Komga",
    authType = TrackerAuthType.SERVER,
) {
    override suspend fun login(credentials: Map<String, String>): Boolean {
        val url = credentials["server_url"] ?: credentials["url"] ?: "http://localhost:25600"
        val user = credentials["username"]?.takeIf { it.isNotBlank() } ?: "KomgaUser"
        val token = credentials["password"] ?: credentials["token"] ?: ""
        setLoggedIn(true, user = user, authToken = token, url = url)
        return true
    }

    override suspend fun search(query: String): List<TrackSearchResult> {
        return listOf(
            TrackSearchResult(id, 6001L, query, 0, "", "", "Komga result for $query"),
        )
    }

    override suspend fun findRemote(track: DesktopTrackRecord): DesktopTrackRecord? = track
    override suspend fun updateRemote(track: DesktopTrackRecord): DesktopTrackRecord = track
}

class MangaUpdatesTracker(id: Long = 7L) : BaseDesktopTracker(
    id = id,
    name = "MangaUpdates",
    authType = TrackerAuthType.CREDENTIALS,
) {
    override suspend fun login(credentials: Map<String, String>): Boolean {
        val user = credentials["username"]?.takeIf { it.isNotBlank() } ?: "MUUser"
        val pass = credentials["password"] ?: credentials["token"] ?: "token"
        setLoggedIn(true, user = user, authToken = pass)
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
                "MangaUpdates result for $query",
            ),
        )
    }

    override suspend fun findRemote(track: DesktopTrackRecord): DesktopTrackRecord? = track
    override suspend fun updateRemote(track: DesktopTrackRecord): DesktopTrackRecord = track
}

class KavitaTracker(id: Long = 8L) : BaseDesktopTracker(
    id = id,
    name = "Kavita",
    authType = TrackerAuthType.SERVER,
) {
    override suspend fun login(credentials: Map<String, String>): Boolean {
        val url = credentials["server_url"] ?: credentials["url"] ?: "http://localhost:5000"
        val user = credentials["username"]?.takeIf { it.isNotBlank() } ?: "KavitaUser"
        val token = credentials["password"] ?: credentials["token"] ?: ""
        setLoggedIn(true, user = user, authToken = token, url = url)
        return true
    }

    override suspend fun search(query: String): List<TrackSearchResult> {
        return listOf(
            TrackSearchResult(id, 8001L, query, 0, "", "", "Kavita result for $query"),
        )
    }

    override suspend fun findRemote(track: DesktopTrackRecord): DesktopTrackRecord? = track
    override suspend fun updateRemote(track: DesktopTrackRecord): DesktopTrackRecord = track
}

class SuwayomiTracker(id: Long = 9L) : BaseDesktopTracker(
    id = id,
    name = "Suwayomi",
    authType = TrackerAuthType.SERVER,
) {
    override suspend fun login(credentials: Map<String, String>): Boolean {
        val url = credentials["server_url"] ?: credentials["url"] ?: "http://localhost:4567"
        val user = credentials["username"]?.takeIf { it.isNotBlank() } ?: "SuwayomiUser"
        val token = credentials["password"] ?: credentials["token"] ?: ""
        setLoggedIn(true, user = user, authToken = token, url = url)
        return true
    }

    override suspend fun search(query: String): List<TrackSearchResult> {
        return listOf(
            TrackSearchResult(id, 9001L, query, 0, "", "", "Suwayomi result for $query"),
        )
    }

    override suspend fun findRemote(track: DesktopTrackRecord): DesktopTrackRecord? = track
    override suspend fun updateRemote(track: DesktopTrackRecord): DesktopTrackRecord = track
}
