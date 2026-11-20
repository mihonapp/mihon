package eu.kanade.presentation.browse.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import eu.kanade.presentation.library.components.CommonMangaItemDefaults
import eu.kanade.presentation.library.components.GridItemTitle
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.CollectionsBookmark
import tachiyomi.presentation.core.components.Badge

@Composable
internal fun InLibraryBadge(enabled: Boolean) {
    if (enabled) {
        Badge(
            imageVector = MaterialSymbols.Rounded.CollectionsBookmark,
        )
    }
}

/**
 * Single line listing the categories of a library entry, formatted like the entry title
 *
 * @param onCover drawn over a cover, so styled like the cover title overlay
 */
@Composable
internal fun CategoriesLabel(
    categories: String?,
    onCover: Boolean,
    modifier: Modifier = Modifier,
) {
    if (categories == null) return

    GridItemTitle(
        title = categories,
        modifier = modifier,
        style = if (onCover) {
            MaterialTheme.typography.titleSmall.copy(
                color = Color.White,
                shadow = CommonMangaItemDefaults.CoverTextShadow,
            )
        } else {
            MaterialTheme.typography.titleSmall
        },
        minLines = 1,
        maxLines = 1,
    )
}
