package eu.kanade.tachiyomi.data.library

internal class ChapterNotificationPrivacy(private val hiddenMangaIds: Set<Long>) {
    private val hiddenLegacyIds = hiddenMangaIds.mapTo(HashSet()) { it.hashCode() }

    fun isHidden(notificationId: Int, mangaId: Long?): Boolean {
        return if (mangaId != null) mangaId in hiddenMangaIds else notificationId in hiddenLegacyIds
    }

    fun summaryChange(
        summaryMangaIds: List<Long>?,
        visibleChildMangaIds: List<Long?>,
        childrenRemoved: Boolean,
    ): SummaryChange? {
        if (summaryMangaIds != null) {
            val remaining = summaryMangaIds.filterNot { it in hiddenMangaIds }
            if (remaining.size == summaryMangaIds.size && (remaining.isNotEmpty() || !childrenRemoved)) return null
            if (remaining.isNotEmpty()) return SummaryChange.Replace(remaining, remaining.size)
            if (visibleChildMangaIds.isEmpty()) return SummaryChange.Cancel
        }

        // Old summaries can contain a title whose child was already dismissed.
        return SummaryChange.Replace(visibleChildMangaIds.filterNotNull(), visibleChildMangaIds.size)
    }

    sealed interface SummaryChange {
        data object Cancel : SummaryChange
        data class Replace(val mangaIds: List<Long>, val mangaCount: Int) : SummaryChange
    }
}
