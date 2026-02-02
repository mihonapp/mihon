package tachiyomi.domain.manga.interactor

import tachiyomi.domain.manga.model.MangaWithChapterCount
import tachiyomi.domain.manga.repository.DuplicateGroup
import tachiyomi.domain.manga.repository.DuplicatePair
import tachiyomi.domain.manga.repository.MangaRepository

enum class DuplicateMatchMode {
    EXACT,      // Exact title match (case-insensitive, trimmed)
    CONTAINS,   // One title contains another
    URL,        // Same URL within the same extension/source
}

/**
 * Interactor to find duplicate novels in the library.
 * Uses database queries for efficient duplicate detection without blocking UI thread.
 */
class FindDuplicateNovels(
    private val mangaRepository: MangaRepository,
) {
    /**
     * Find duplicate groups using exact matching (case-insensitive, trimmed).
     * Returns groups of manga IDs that share the same normalized title.
     */
    suspend fun findExact(): List<DuplicateGroup> {
        return mangaRepository.findDuplicatesExact()
    }

    /**
     * Find duplicate pairs using contains matching.
     * Returns pairs where one title contains another.
     */
    suspend fun findContains(): List<DuplicatePair> {
        return mangaRepository.findDuplicatesContains()
    }

    /**
     * Get manga with chapter counts for a list of IDs.
     * Used to get full manga info after finding duplicates.
     */
    suspend fun getMangaWithCounts(ids: List<Long>): List<MangaWithChapterCount> {
        return mangaRepository.getMangaWithCounts(ids)
    }

    /**
     * Find potential similar novels for a specific manga (excluding itself).
     * Returns novels in library that match or contain the title.
     */
    suspend fun findSimilarTo(mangaId: Long, title: String): List<MangaWithChapterCount> {
        val normalizedTitle = title.lowercase().trim()
        
        // Find exact matches
        val exactMatches = mangaRepository.findDuplicatesExact()
            .find { group -> group.ids.contains(mangaId) }
            ?.ids?.filter { it != mangaId }
            ?: emptyList()
        
        // Find contains matches
        val containsMatches = mangaRepository.findDuplicatesContains()
            .filter { it.idA == mangaId || it.idB == mangaId }
            .flatMap { listOf(it.idA, it.idB) }
            .filter { it != mangaId }
            .distinct()
        
        // Combine and deduplicate
        val allMatchIds = (exactMatches + containsMatches).distinct()
        
        return getMangaWithCounts(allMatchIds).sortedByDescending { it.chapterCount }
    }

    /**
     * Find duplicates by URL within the same extension.
     * Returns groups where multiple manga have the same URL from the same source.
     */
    suspend fun findUrlDuplicates(): List<DuplicateGroup> {
        return mangaRepository.findDuplicatesByUrl()
    }

    /**
     * Find duplicates and return full manga info with chapter counts.
     * Groups results by normalized title.
     */
    suspend fun findDuplicatesGrouped(mode: DuplicateMatchMode): Map<String, List<MangaWithChapterCount>> {
        return when (mode) {
            DuplicateMatchMode.EXACT -> {
                val groups = findExact()
                val allIds = groups.flatMap { it.ids }
                val mangaMap = getMangaWithCounts(allIds).associateBy { it.manga.id }
                
                groups.mapNotNull { group ->
                    val mangaList = group.ids.mapNotNull { mangaMap[it] }
                    if (mangaList.size > 1) {
                        group.normalizedTitle to mangaList.sortedByDescending { it.chapterCount }
                    } else {
                        null
                    }
                }.toMap()
            }
            DuplicateMatchMode.CONTAINS -> {
                val pairs = findContains()
                // Group pairs by the shorter title (the one that's contained)
                val allIds = pairs.flatMap { listOf(it.idA, it.idB) }.distinct()
                val mangaMap = getMangaWithCounts(allIds).associateBy { it.manga.id }
                
                // Build groups from pairs
                val groups = mutableMapOf<String, MutableSet<Long>>()
                pairs.forEach { pair ->
                    val keyA = pair.titleA.lowercase().trim()
                    val keyB = pair.titleB.lowercase().trim()
                    val key = if (keyA.length <= keyB.length) keyA else keyB
                    
                    groups.getOrPut(key) { mutableSetOf() }.apply {
                        add(pair.idA)
                        add(pair.idB)
                    }
                }
                
                groups.mapNotNull { (title, ids) ->
                    val mangaList = ids.mapNotNull { mangaMap[it] }
                    if (mangaList.size > 1) {
                        title to mangaList.sortedByDescending { it.chapterCount }
                    } else {
                        null
                    }
                }.toMap()
            }
            DuplicateMatchMode.URL -> {
                // Find duplicates by URL within the same source/extension
                val groups = findUrlDuplicates()
                val allIds = groups.flatMap { it.ids }
                val mangaMap = getMangaWithCounts(allIds).associateBy { it.manga.id }
                
                groups.mapNotNull { group ->
                    val mangaList = group.ids.mapNotNull { mangaMap[it] }
                    if (mangaList.size > 1) {
                        // Use URL as the group key for URL mode
                        group.normalizedTitle to mangaList.sortedByDescending { it.chapterCount }
                    } else {
                        null
                    }
                }.toMap()
            }
        }
    }
}
