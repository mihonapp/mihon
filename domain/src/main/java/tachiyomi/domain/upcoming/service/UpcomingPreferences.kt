package tachiyomi.domain.upcoming.service

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getLongArray

@Inject
@SingleIn(AppScope::class)
class UpcomingPreferences(
    preferenceStore: PreferenceStore,
) {

    val filterIncludedCategories: Preference<List<Long>> = preferenceStore.getLongArray(
        FILTER_INCLUDED_CATEGORIES_KEY,
        emptyList(),
    )

    val filterExcludedCategories: Preference<List<Long>> = preferenceStore.getLongArray(
        FILTER_EXCLUDED_CATEGORIES_KEY,
        emptyList(),
    )

    companion object {
        const val FILTER_INCLUDED_CATEGORIES_KEY = "pref_filter_upcoming_included_categories"
        const val FILTER_EXCLUDED_CATEGORIES_KEY = "pref_filter_upcoming_excluded_categories"
    }
}
