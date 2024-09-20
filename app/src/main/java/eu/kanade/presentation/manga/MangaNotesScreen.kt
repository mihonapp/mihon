package eu.kanade.presentation.manga

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarTitle
import eu.kanade.presentation.manga.components.MangaNotesTextArea
import eu.kanade.tachiyomi.ui.manga.notes.MangaNotesScreenState
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun MangaNotesScreen(
    state: MangaNotesScreenState.Success,
    navigateUp: () -> Unit,
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                titleContent = {
                    AppBarTitle(title = stringResource(MR.strings.action_notes), subtitle = state.manga.title)
                },
                navigateUp = navigateUp,
                scrollBehavior = scrollBehavior,
            )
        },
        modifier = modifier
            .imePadding(),
    ) { paddingValues ->
        MangaNotesTextArea(
            state = state,
            onSave = onSave,
            modifier = Modifier
                .padding(
                    top = paddingValues.calculateTopPadding() + MaterialTheme.padding.small,
                    bottom = MaterialTheme.padding.small,
                )
                .padding(horizontal = MaterialTheme.padding.small)
                .windowInsetsPadding(
                    WindowInsets.navigationBars
                        .only(WindowInsetsSides.Bottom),
                ),
        )
    }
}
