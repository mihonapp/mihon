package eu.kanade.tachiyomi.data.library

import dev.icerock.moko.resources.StringResource
import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MANGA_HAS_UNREAD
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MANGA_NON_COMPLETED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MANGA_NON_READ
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MANGA_OUTSIDE_RELEASE_PERIOD
import tachiyomi.domain.manga.interactor.FetchInterval
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import kotlin.time.Clock

@Inject
class LibraryUpdateFilter(
    private val libraryPreferences: LibraryPreferences,
    private val fetchInterval: FetchInterval,
) {
    fun filter(
        libraryManga: List<LibraryManga>,
        categoryId: Long = -1L,
        onSkipped: (Manga, StringResource) -> Unit = { _, _ -> },
    ): List<LibraryManga> {
        val candidates = if (categoryId != -1L) {
            libraryManga.filter { categoryId in it.categories }
        } else {
            val includedCategories = libraryPreferences.updateCategories.get().map { it.toLong() }
            val excludedCategories = libraryPreferences.updateCategoriesExclude.get().map { it.toLong() }

            libraryManga.filter {
                val included = includedCategories.isEmpty() || it.categories.intersect(includedCategories).isNotEmpty()
                val excluded = it.categories.intersect(excludedCategories).isNotEmpty()
                included && !excluded
            }
        }

        val restrictions = libraryPreferences.autoUpdateMangaRestrictions.get()
        val timeZone = TimeZone.currentSystemDefault()
        val fetchWindowUpperBound = fetchInterval.getWindow(
            Clock.System.now().toLocalDateTime(timeZone).date,
            timeZone,
        ).endInclusive

        return candidates.filter { entry ->
            val manga = entry.manga
            val nextUpdate = manga.nextUpdate
            val reason = when {
                manga.updateStrategy == UpdateStrategy.ONLY_FETCH_ONCE && entry.totalChapters > 0L ->
                    MR.strings.skipped_reason_not_always_update
                MANGA_NON_COMPLETED in restrictions && manga.status.toInt() == SManga.COMPLETED ->
                    MR.strings.skipped_reason_completed
                MANGA_HAS_UNREAD in restrictions && entry.unreadCount != 0L ->
                    MR.strings.skipped_reason_not_caught_up
                MANGA_NON_READ in restrictions && entry.totalChapters > 0L && !entry.hasStarted ->
                    MR.strings.skipped_reason_not_started
                MANGA_OUTSIDE_RELEASE_PERIOD in restrictions &&
                    nextUpdate != null && nextUpdate > fetchWindowUpperBound ->
                    MR.strings.skipped_reason_not_in_release_period
                else -> null
            }
            if (reason != null) onSkipped(manga, reason)
            reason == null
        }
    }
}
