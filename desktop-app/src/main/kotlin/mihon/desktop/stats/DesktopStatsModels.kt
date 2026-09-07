package mihon.desktop.stats

data class OverviewStats(
    val libraryMangaCount: Int = 0,
    val completedMangaCount: Int = 0,
    val totalReadDurationMillis: Long = 0L,
    val formattedReadDuration: String = "0m",
    val totalChapterCount: Int = 0,
    val readChapterCount: Int = 0,
    val unreadChapterCount: Int = 0,
    val readPercentage: Float = 0f,
)

data class StatusDistribution(
    val ongoingCount: Int = 0,
    val completedCount: Int = 0,
    val onHiatusCount: Int = 0,
    val cancelledCount: Int = 0,
    val unknownCount: Int = 0,
)

data class ReadingProgressDistribution(
    val unreadCount: Int = 0,
    val inProgressCount: Int = 0,
    val finishedCount: Int = 0,
)

data class GenreStatItem(
    val genre: String,
    val count: Int,
    val percentage: Float,
)

data class CategoryStatItem(
    val categoryName: String,
    val mangaCount: Int,
)

data class TrackerStats(
    val trackedMangaCount: Int = 0,
    val meanScore: Double = 0.0,
    val trackerCount: Int = 0,
)

data class DesktopStatsData(
    val overview: OverviewStats = OverviewStats(),
    val statuses: StatusDistribution = StatusDistribution(),
    val progress: ReadingProgressDistribution = ReadingProgressDistribution(),
    val topGenres: List<GenreStatItem> = emptyList(),
    val categories: List<CategoryStatItem> = emptyList(),
    val tracking: TrackerStats = TrackerStats(),
    val isLoading: Boolean = false,
)
