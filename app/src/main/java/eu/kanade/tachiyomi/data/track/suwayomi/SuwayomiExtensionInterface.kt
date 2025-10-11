package eu.kanade.tachiyomi.data.track.suwayomi

public data class TrackMangaFragment(
    public val artist: String?,
    public val author: String?,
    public val description: String?,
    public val id: Int,
    public val status: String,
    public val thumbnailUrl: String?,
    public val title: String,
    public val url: String,
    public val genre: List<String>,
    public val inLibraryAt: Long,
    public val chapters: Int,
    public val latestReadChapter: Double?,
    public val unreadCount: Int,
    public val downloadCount: Int,
)

interface SuwayomiExtensionInterface {
    suspend fun getTrackSearch(mangaId: Int): TrackMangaFragment
    suspend fun updateProgress(mangaId: Int, lastChapterReadNumber: Double): Unit
}
