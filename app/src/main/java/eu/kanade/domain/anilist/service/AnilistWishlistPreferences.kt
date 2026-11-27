package eu.kanade.domain.anilist.service

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

@Inject
@SingleIn(AppScope::class)
class AnilistWishlistPreferences(
    preferenceStore: PreferenceStore,
) {
    val categoryId: Preference<Long> = preferenceStore.getLong(
        Preference.appStateKey("anilist_wishlist_category_id"),
        0L,
    )
}
