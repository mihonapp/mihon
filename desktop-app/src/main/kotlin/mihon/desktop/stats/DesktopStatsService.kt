package mihon.desktop.stats

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import mihon.desktop.library.repository.LibraryRepository

class DesktopStatsService(
    private val repository: LibraryRepository,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val _stats = MutableStateFlow(DesktopStatsData(isLoading = true))
    val stats: StateFlow<DesktopStatsData> = _stats.asStateFlow()

    suspend fun refresh() {
        _stats.update { it.copy(isLoading = true) }
        val data = computeStats()
        _stats.update { data }
    }

    suspend fun computeStats(): DesktopStatsData = withContext(Dispatchers.IO) {
        val allManga = repository.allMangaSnapshot().filter { it.favorite }
        val allChapters = repository.allChaptersSnapshot()
        val allHistory = repository.allHistorySnapshot()
        val allTracking = repository.allTrackingSnapshot()
        val categories = repository.allCategoriesSnapshot()
        val categoryLinks = repository.mangaCategoryLinksSnapshot()

        val mangaById = allManga.associateBy { it.id }
        val libraryChapters = allChapters.filter { it.mangaId in mangaById }
        val totalChapters = libraryChapters.size
        val readChapters = libraryChapters.count { it.read }
        val unreadChapters = totalChapters - readChapters
        val readPercentage = if (totalChapters > 0) {
            (readChapters.toFloat() / totalChapters.toFloat()) * 100f
        } else {
            0f
        }

        val chaptersByManga = libraryChapters.groupBy { it.mangaId }
        var unreadManga = 0
        var inProgressManga = 0
        var finishedManga = 0

        for (manga in allManga) {
            val chapters = chaptersByManga[manga.id].orEmpty()
            if (chapters.isEmpty()) {
                unreadManga++
            } else {
                val read = chapters.count { it.read }
                when {
                    read == 0 -> unreadManga++
                    read == chapters.size -> finishedManga++
                    else -> inProgressManga++
                }
            }
        }

        val totalReadDurationMillis = allHistory.sumOf { it.readDuration }
        val formattedDuration = formatDuration(totalReadDurationMillis)

        val completedMangaCount = allManga.count {
            it.status == 2L && (chaptersByManga[it.id]?.all { c -> c.read } ?: false)
        }

        val overview = OverviewStats(
            libraryMangaCount = allManga.size,
            completedMangaCount = completedMangaCount,
            totalReadDurationMillis = totalReadDurationMillis,
            formattedReadDuration = formattedDuration,
            totalChapterCount = totalChapters,
            readChapterCount = readChapters,
            unreadChapterCount = unreadChapters,
            readPercentage = readPercentage,
        )

        var ongoingCount = 0
        var statusCompletedCount = 0
        var onHiatusCount = 0
        var cancelledCount = 0
        var unknownCount = 0

        for (manga in allManga) {
            when (manga.status) {
                1L -> ongoingCount++
                2L -> statusCompletedCount++
                5L -> cancelledCount++
                6L -> onHiatusCount++
                else -> unknownCount++
            }
        }
        val statuses = StatusDistribution(
            ongoingCount = ongoingCount,
            completedCount = statusCompletedCount,
            onHiatusCount = onHiatusCount,
            cancelledCount = cancelledCount,
            unknownCount = unknownCount,
        )

        val progress = ReadingProgressDistribution(
            unreadCount = unreadManga,
            inProgressCount = inProgressManga,
            finishedCount = finishedManga,
        )

        val genreCountMap = mutableMapOf<String, Int>()
        for (manga in allManga) {
            val genres = decodeGenres(manga.genreJson)
            for (g in genres) {
                val trimmed = g.trim()
                if (trimmed.isNotBlank()) {
                    genreCountMap[trimmed] = (genreCountMap[trimmed] ?: 0) + 1
                }
            }
        }
        val topGenres = genreCountMap.entries
            .sortedByDescending { it.value }
            .take(10)
            .map { (genre, count) ->
                val pct = if (allManga.isNotEmpty()) (count.toFloat() / allManga.size) * 100f else 0f
                GenreStatItem(genre = genre, count = count, percentage = pct)
            }

        val mangaCountByCategory = mutableMapOf<Long, Int>()
        for ((mangaId, catIds) in categoryLinks) {
            if (mangaId in mangaById) {
                for (catId in catIds) {
                    mangaCountByCategory[catId] = (mangaCountByCategory[catId] ?: 0) + 1
                }
            }
        }
        val categoryStats = categories.map { cat ->
            CategoryStatItem(
                categoryName = cat.name,
                mangaCount = mangaCountByCategory[cat.id] ?: 0,
            )
        }.sortedByDescending { it.mangaCount }

        val libraryMangaIds = mangaById.keys
        val validTracks = allTracking.filter { it.mangaId in libraryMangaIds }
        val trackedMangaIds = validTracks.map { it.mangaId }.distinct()
        val scoredTracks = validTracks.filter { it.score > 0.0 }
        val meanScore = if (scoredTracks.isNotEmpty()) {
            scoredTracks.map { it.score }.average()
        } else {
            0.0
        }
        val uniqueTrackers = validTracks.map { it.trackerId }.distinct().size
        val tracking = TrackerStats(
            trackedMangaCount = trackedMangaIds.size,
            meanScore = meanScore,
            trackerCount = uniqueTrackers,
        )

        DesktopStatsData(
            overview = overview,
            statuses = statuses,
            progress = progress,
            topGenres = topGenres,
            categories = categoryStats,
            tracking = tracking,
            isLoading = false,
        )
    }

    private fun decodeGenres(raw: String): List<String> = try {
        json.decodeFromString<List<String>>(raw)
    } catch (_: Exception) {
        emptyList()
    }

    private fun formatDuration(millis: Long): String {
        if (millis <= 0L) return "0m"
        val seconds = millis / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        val remHours = hours % 24
        val remMinutes = minutes % 60

        return when {
            days > 0 -> "${days}d ${remHours}h ${remMinutes}m"
            hours > 0 -> "${hours}h ${remMinutes}m"
            else -> "${minutes}m"
        }
    }
}
