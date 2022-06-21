package eu.kanade.domain.track.model

data class Track(
    val id: Long,
    val mangaId: Long,
    val syncId: Long,
    val remoteId: Long,
    val libraryId: Long?,
    val title: String,
    val lastChapterRead: Double,
    val totalChapters: Long,
    val status: Long,
    val score: Float,
    val remoteUrl: String,
    val startDate: Long,
    val finishDate: Long,
) {
    fun copyPersonalFrom(other: Track): Track {
        return this.copy(
            lastChapterRead = other.lastChapterRead,
            score = other.score,
            status = other.status,
            startDate = other.startDate,
            finishDate = other.finishDate,
        )
    }
}
