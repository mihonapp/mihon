package eu.kanade.tachiyomi.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import eu.kanade.tachiyomi.ui.home.HomeRoute
import eu.kanade.tachiyomi.ui.home.HomeScreen

fun EntryProviderScope<NavKey>.appEntries() {
    entry<HomeRoute> {
        HomeScreen()
    }
}
