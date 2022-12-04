package eu.kanade.presentation.util

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import cafe.adriel.voyager.navigator.Navigator

/**
 * For invoking back press to the parent activity
 */
val LocalBackPress: ProvidableCompositionLocal<(() -> Unit)?> = staticCompositionLocalOf { null }

interface Tab : cafe.adriel.voyager.navigator.tab.Tab {
    suspend fun onReselect(navigator: Navigator) {}
}
