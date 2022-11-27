package eu.kanade.presentation.browse

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import eu.kanade.presentation.components.Badge
import eu.kanade.tachiyomi.R

@Composable
fun InLibraryBadge(enabled: Boolean) {
    if (enabled) {
        Badge(text = stringResource(R.string.in_library))
    }
}
