package eu.kanade.presentation.manga

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import eu.kanade.presentation.components.AppBarTitle
import eu.kanade.presentation.components.UpIcon
import eu.kanade.presentation.manga.components.MangaNotesTextArea
import eu.kanade.tachiyomi.ui.manga.notes.MangaNotesScreen
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding

@Composable
fun MangaNotesScreen(
    state: MangaNotesScreen.State,
    navigateUp: () -> Unit,
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        topBar = { topBarScrollBehavior ->
            LargeTopAppBar(
                title = {
                    AppBarTitle(
                        title = state.manga.title,
                        modifier = Modifier
                            .padding(horizontal = MaterialTheme.padding.small),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = navigateUp) {
                        UpIcon()
                    }
                },
                expandedHeight = TopAppBarDefaults.MediumAppBarExpandedHeight,
                scrollBehavior = topBarScrollBehavior,
            )
        },
        topBarScrollBehavior = scrollBehavior,
        modifier = modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .imePadding(),
    ) { paddingValues ->
        MangaNotesTextArea(
            state = state,
            onSave = onSave,
            modifier = Modifier
                .padding(horizontal = MaterialTheme.padding.small)
                .padding(top = paddingValues.calculateTopPadding())
                .windowInsetsPadding(
                    WindowInsets.navigationBars
                        .only(WindowInsetsSides.Bottom),
                ),
        )
    }
}
