package tachiyomi.domain.upcoming.service

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getLongArray

class UpcomingPreferences(
    preferenceStore: PreferenceStore,
) {

    val filterIncludedCategories: Preference<List<Long>> = preferenceStore.getLongArray(
        "pref_filter_upcoming_included_categories",
        emptyList(),
    )

    val filterExcludedCategories: Preference<List<Long>> = preferenceStore.getLongArray(
        "pref_filter_upcoming_excluded_categories",
        emptyList(),
    )
}
