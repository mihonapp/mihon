package mihon.desktop.track

import kotlinx.serialization.Serializable

@Serializable
enum class TrackStatus(val value: Long, val label: String) {
    READING(1L, "Reading"),
    COMPLETED(2L, "Completed"),
    ON_HOLD(3L, "On Hold"),
    DROPPED(4L, "Dropped"),
    PLAN_TO_READ(5L, "Plan to Read"),
    REREADING(6L, "Rereading"),
    ;

    companion object {
        fun fromValue(value: Long): TrackStatus = entries.find { it.value == value } ?: READING
    }
}

@Serializable
data class DesktopTrackRecord(
    val id: Long = 0,
    val mangaId: Long,
    val trackerId: Long,
    val remoteId: Long,
    val libraryId: Long = 0,
    val title: String,
    val lastChapterRead: Double = 0.0,
    val totalChapters: Long = 0,
    val score: Double = 0.0,
    val status: Long = TrackStatus.READING.value,
    val startedReadingDate: Long = 0,
    val finishedReadingDate: Long = 0,
    val private: Boolean = false,
    val trackingUrl: String = "",
)

@Serializable
data class TrackSearchResult(
    val trackerId: Long,
    val remoteId: Long,
    val title: String,
    val totalChapters: Long = 0,
    val coverUrl: String = "",
    val trackingUrl: String = "",
    val summary: String = "",
)

enum class ConflictResolutionPolicy {
    LOCAL_WINS,
    REMOTE_WINS,
    PROMPT,
}

data class TrackConflict(
    val trackerId: Long,
    val trackerName: String,
    val mangaId: Long,
    val mangaTitle: String,
    val localChapterRead: Double,
    val remoteChapterRead: Double,
    val localStatus: Long,
    val remoteStatus: Long,
    val localScore: Double,
    val remoteScore: Double,
)
