package eu.kanade.presentation.library.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.PreviewLightDark
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import eu.kanade.tachiyomi.ui.library.LibraryItem
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Folder
import mihon.icons.materialsymbols.rounded.Person
import mihon.icons.materialsymbols.rounded.Star
import mihon.icons.materialsymbols.roundedfilled.Warning
import tachiyomi.domain.manga.model.sourceStats
import tachiyomi.presentation.core.components.Badge

@Composable
internal fun DownloadsBadge(count: Int) {
    if (count > 0) {
        Badge(
            text = "$count",
            color = MaterialTheme.colorScheme.tertiary,
            textColor = MaterialTheme.colorScheme.onTertiary,
        )
    }
}

@Composable
internal fun UnreadBadge(count: Long) {
    if (count > 0) {
        Badge(text = "$count")
    }
}

@Composable
internal fun LibraryEndBadges(
    item: LibraryItem,
    showUpdateErrors: Boolean,
    showSourceRating: Boolean,
) {
    if (showSourceRating) {
        val stats = item.libraryManga.manga.sourceStats()
        Column(horizontalAlignment = Alignment.End) {
            StatBadge(
                imageVector = MaterialSymbols.Rounded.Star,
                text = stats.rating?.let(::formatRating) ?: "?",
            )
            StatBadge(
                imageVector = MaterialSymbols.Rounded.Person,
                text = stats.ratingCount?.let(::formatCount) ?: "?",
            )
        }
    }
    if (showUpdateErrors && item.hasUpdateError) {
        Badge(
            imageVector = MaterialSymbols.RoundedFilled.Warning,
            color = MaterialTheme.colorScheme.error,
            iconColor = MaterialTheme.colorScheme.onError,
        )
    }
    LanguageBadge(
        isLocal = item.badges.isLocal,
        sourceLanguage = item.badges.sourceLanguage,
    )
}

@Composable
private fun StatBadge(
    imageVector: ImageVector,
    text: String,
) {
    Badge(
        text = text,
        imageVector = imageVector,
    )
}

private fun formatRating(value: Double): String {
    val rounded = kotlin.math.round(value * 10) / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}

private fun formatCount(count: Long): String {
    return when {
        count >= 1_000_000 -> "${count / 1_000_000}m"
        count >= 10_000 -> "${count / 1_000}k"
        else -> count.toString()
    }
}

@Composable
internal fun LanguageBadge(
    isLocal: Boolean,
    sourceLanguage: String,
) {
    if (isLocal) {
        Badge(
            imageVector = MaterialSymbols.Rounded.Folder,
            color = MaterialTheme.colorScheme.tertiary,
            iconColor = MaterialTheme.colorScheme.onTertiary,
        )
    } else if (sourceLanguage.isNotEmpty()) {
        Badge(
            text = sourceLanguage.uppercase(),
            color = MaterialTheme.colorScheme.tertiary,
            textColor = MaterialTheme.colorScheme.onTertiary,
        )
    }
}

@PreviewLightDark
@Composable
private fun BadgePreview() {
    TachiyomiPreviewTheme {
        Column {
            DownloadsBadge(count = 10)
            UnreadBadge(count = 10)
            LanguageBadge(isLocal = true, sourceLanguage = "EN")
            LanguageBadge(isLocal = false, sourceLanguage = "EN")
        }
    }
}
