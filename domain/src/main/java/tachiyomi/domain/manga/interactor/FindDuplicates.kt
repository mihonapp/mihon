package tachiyomi.domain.manga.interactor

import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.source.model.StubSource
import tachiyomi.domain.source.service.SourceManager
import kotlin.math.max

enum class DuplicateSearchMode {
    EXACT,
    FUZZY,
}

data class DuplicateEntry(
    val manga: Manga,
    val chapterCount: Long,
    val readChapterCount: Long,
    val isAlive: Boolean,
    val isOrphaned: Boolean = false,
    val isSuggested: Boolean,
)

data class DuplicateGroup(
    val mainTitle: String,
    val entries: List<DuplicateEntry>,
    val selectedId: Long,
)

class FindDuplicates(
    private val mangaRepository: MangaRepository,
    private val getChaptersByMangaId: GetChaptersByMangaId,
    private val sourceManager: SourceManager,
    private val isSourceOrphaned: (Long) -> Boolean = { false },
) {
    private data class ProbedInfo(
        val manga: Manga,
        val chapterCount: Long,
        val readCount: Long,
        val isAlive: Boolean,
        val isOrphaned: Boolean,
        val score: Long,
    )

    suspend fun await(
        mode: DuplicateSearchMode,
        threshold: Float,
        onProgress: suspend (processed: Int, total: Int) -> Unit,
    ): List<DuplicateGroup> {
        val favorites = mangaRepository.getFavorites()
        val processedIds = HashSet<Long>()
        val resultGroups = mutableListOf<DuplicateGroup>()
        val totalCount = favorites.size

        for (i in favorites.indices) {
            onProgress(i + 1, totalCount)
            val entryA = favorites[i]
            if (entryA.id in processedIds) continue

            val currentMatches = mutableListOf<Manga>()
            currentMatches.add(entryA)

            for (j in i + 1 until favorites.size) {
                val entryB = favorites[j]
                if (entryB.id in processedIds) continue

                if (isMatch(entryA, entryB, mode, threshold)) {
                    currentMatches.add(entryB)
                    processedIds.add(entryB.id)
                }
            }

            if (currentMatches.size > 1) {
                processedIds.add(entryA.id)

                val probedEntries = currentMatches.map { manga ->
                    val source = sourceManager.get(manga.source)
                    val isAlive = source != null && source !is StubSource
                    val isOrphaned = isSourceOrphaned(manga.source)
                    val chapters = getChaptersByMangaId.await(manga.id)
                    val count = chapters.size.toLong()
                    val readCount = chapters.count { it.read }.toLong()

                    val aliveTierScore = when {
                        !isAlive -> 0L
                        isOrphaned -> 100_000L
                        else -> 2_000_000L
                    }

                    val score = aliveTierScore +
                        count * 1_000L +
                        readCount * 100L +
                        (if (manga.favorite) 10L else 0L)

                    ProbedInfo(
                        manga = manga,
                        chapterCount = count,
                        readCount = readCount,
                        isAlive = isAlive,
                        isOrphaned = isOrphaned,
                        score = score,
                    )
                }

                val maxScore = probedEntries.maxOf { it.score }
                var suggestedChosen = false

                val duplicateEntries = probedEntries.map { info ->
                    val isSuggested = !suggestedChosen && info.score == maxScore
                    if (isSuggested) suggestedChosen = true
                    DuplicateEntry(
                        manga = info.manga,
                        chapterCount = info.chapterCount,
                        readChapterCount = info.readCount,
                        isAlive = info.isAlive,
                        isOrphaned = info.isOrphaned,
                        isSuggested = isSuggested,
                    )
                }

                val suggestedId = duplicateEntries.firstOrNull { it.isSuggested }?.manga?.id
                    ?: entryA.id

                resultGroups.add(
                    DuplicateGroup(
                        mainTitle = entryA.title,
                        entries = duplicateEntries,
                        selectedId = suggestedId,
                    ),
                )
            }
        }

        return resultGroups
    }

    private fun isMatch(
        mangaA: Manga,
        mangaB: Manga,
        mode: DuplicateSearchMode,
        threshold: Float,
    ): Boolean {
        val cleanA = sanitizeTitle(mangaA.title)
        val cleanB = sanitizeTitle(mangaB.title)

        if (cleanA.isEmpty() || cleanB.isEmpty()) return false

        if (mode == DuplicateSearchMode.EXACT) {
            return cleanA.equals(cleanB, ignoreCase = true)
        }

        // FUZZY Mode
        if (cleanA.equals(cleanB, ignoreCase = true)) return true

        val titleSim = similarity(cleanA, cleanB)
        if (titleSim >= threshold) return true

        // Description alternative title matching
        val descA = mangaA.description.orEmpty()
        if (descA.isNotEmpty() && checkDescriptionMatch(descA, cleanB, threshold)) {
            return true
        }

        val descB = mangaB.description.orEmpty()
        if (descB.isNotEmpty() && checkDescriptionMatch(descB, cleanA, threshold)) {
            return true
        }

        return false
    }

    private fun checkDescriptionMatch(description: String, targetCleanTitle: String, threshold: Float): Boolean {
        val lines = description.lines()
        for (rawLine in lines) {
            val line = sanitizeTitle(rawLine)
            if (line.length < 3) continue
            if (line.contains(targetCleanTitle) || targetCleanTitle.contains(line)) {
                return true
            }
            if (similarity(line, targetCleanTitle) >= threshold) {
                return true
            }
        }
        return false
    }

    private fun sanitizeTitle(title: String): String {
        return title
            .lowercase()
            .replace(NOISE_REGEX, "")
            .replace(PUNCTUATION_REGEX, " ")
            .replace(WHITESPACE_REGEX, " ")
            .trim()
    }

    private fun similarity(s1: String, s2: String): Float {
        if (s1 == s2) return 1.0f
        val maxLen = max(s1.length, s2.length)
        if (maxLen == 0) return 1.0f
        val dist = levenshteinDistance(s1, s2)
        return 1.0f - (dist.toFloat() / maxLen.toFloat())
    }

    private fun levenshteinDistance(lhs: String, rhs: String): Int {
        val lhsLength = lhs.length
        val rhsLength = rhs.length
        var cost = IntArray(lhsLength + 1) { it }
        var newCost = IntArray(lhsLength + 1) { 0 }
        for (i in 1..rhsLength) {
            newCost[0] = i
            for (j in 1..lhsLength) {
                val match = if (lhs[j - 1] == rhs[i - 1]) 0 else 1
                val costReplace = cost[j - 1] + match
                val costInsert = cost[j] + 1
                val costDelete = newCost[j - 1] + 1
                newCost[j] = minOf(costInsert, costDelete, costReplace)
            }
            val swap = cost
            cost = newCost
            newCost = swap
        }
        return cost[lhsLength]
    }

    companion object {
        private val NOISE_REGEX = Regex(
            """(?i)\((official|colored|digital|uncensored|webtoon|raw|hd)\)|\[(official|colored|digital|uncensored|webtoon|raw|hd)\]""",
        )
        private val PUNCTUATION_REGEX = Regex("""[^\p{L}\p{N}\s]""")
        private val WHITESPACE_REGEX = Regex("""\s+""")
    }
}
