package eu.kanade.presentation.browse.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Check
import mihon.icons.materialsymbols.rounded.CollectionsBookmark
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.Badge
import tachiyomi.presentation.core.i18n.stringResource

@Composable
internal fun InLibraryBadge(enabled: Boolean) {
    if (enabled) {
        Badge(
            imageVector = MaterialSymbols.Rounded.CollectionsBookmark,
        )
    }
}

@Composable
internal fun SourceSelectionBadge(enabled: Boolean) {
    if (enabled) {
        val description = stringResource(MR.strings.linked_sources_selected)
        Box(Modifier.semantics { contentDescription = description }) {
            Badge(imageVector = MaterialSymbols.Rounded.Check)
        }
    }
}
