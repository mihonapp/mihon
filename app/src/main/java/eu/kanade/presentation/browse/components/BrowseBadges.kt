package eu.kanade.presentation.browse.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.Flow
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.CollectionsBookmark
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.Badge
import tachiyomi.presentation.core.i18n.stringResource

enum class ReadProgress { None, Started, Finished }

@Composable
internal fun InLibraryBadge(enabled: Boolean) {
    if (enabled) {
        Badge(
            imageVector = MaterialSymbols.Rounded.CollectionsBookmark,
        )
    }
}

@Composable
internal fun ReadProgressBadge(manga: Manga, readProgress: ((Manga) -> Flow<ReadProgress>)?) {
    if (readProgress == null) return
    val progress by remember(manga.id) { readProgress(manga) }.collectAsState(ReadProgress.None)
    val text = when (progress) {
        ReadProgress.None -> return
        ReadProgress.Started -> stringResource(MR.strings.label_started)
        ReadProgress.Finished -> stringResource(MR.strings.label_read_chapters)
    }
    Badge(
        text = text,
        color = MaterialTheme.colorScheme.tertiary,
        textColor = MaterialTheme.colorScheme.onTertiary,
    )
}
