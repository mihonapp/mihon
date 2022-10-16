package eu.kanade.presentation.library.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import eu.kanade.presentation.components.Badge
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.library.LibraryItem

@Composable
fun DownloadsBadge(
    enabled: Boolean,
    item: LibraryItem,
) {
    if (enabled && item.downloadCount > 0) {
        Badge(
            text = "${item.downloadCount}",
            color = MaterialTheme.colorScheme.tertiary,
            textColor = MaterialTheme.colorScheme.onTertiary,
        )
    }
}

@Composable
fun UnreadBadge(
    enabled: Boolean,
    item: LibraryItem,
) {
    if (enabled && item.unreadCount > 0) {
        Badge(text = "${item.unreadCount}")
    }
}

@Composable
fun LanguageBadge(
    showLanguage: Boolean,
    showLocal: Boolean,
    item: LibraryItem,
) {
    if (showLocal && item.isLocal) {
        Badge(
            text = stringResource(R.string.local_source_badge),
            color = MaterialTheme.colorScheme.tertiary,
            textColor = MaterialTheme.colorScheme.onTertiary,
        )
    } else if (showLanguage && item.sourceLanguage.isNotEmpty()) {
        Badge(
            text = item.sourceLanguage.uppercase(),
            color = MaterialTheme.colorScheme.tertiary,
            textColor = MaterialTheme.colorScheme.onTertiary,
        )
    }
}
