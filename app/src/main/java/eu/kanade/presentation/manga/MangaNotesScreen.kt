package eu.kanade.presentation.manga

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import com.halilibo.richtext.markdown.Markdown
import com.halilibo.richtext.ui.RichTextStyle
import com.halilibo.richtext.ui.material3.RichText
import com.halilibo.richtext.ui.string.RichTextStringStyle
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarTitle
import eu.kanade.presentation.manga.components.MangaNotesTextArea
import eu.kanade.tachiyomi.ui.manga.notes.MangaNotesScreenState
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.ExtendedFloatingActionButton
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen

@Composable
fun MangaNotesScreen(
    state: MangaNotesScreenState.Success,
    navigateUp: () -> Unit,
    beginEditing: () -> Unit,
    endEditing: () -> Unit,
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
        floatingActionButton = {
            AnimatedVisibility(
                true,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .imePadding(),
            ) {
                ExtendedFloatingActionButton(
                    text = {
                        Text(
                            text = stringResource(
                                if (state.editing) MR.strings.action_apply else MR.strings.action_edit,
                            ),
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = if (state.editing) Icons.Filled.Check else Icons.Filled.Edit,
                            contentDescription = null,
                        )
                    },
                    onClick = { if (state.editing) endEditing() else beginEditing() },
                    expanded = true,
                )
            }
        },
        modifier = modifier
            .imePadding(),
    ) { paddingValues ->
        AnimatedVisibility(
            state.editing,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
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

        AnimatedVisibility(
            !state.editing && state.notes.isNullOrBlank(),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            EmptyScreen(
                stringRes = MR.strings.information_no_notes,
                modifier = Modifier.padding(paddingValues),
            )
        }

        AnimatedVisibility(
            !state.editing && !state.notes.isNullOrBlank(),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            RichText(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth()
                    .padding(
                        horizontal = MaterialTheme.padding.medium,
                        vertical = paddingValues.calculateTopPadding() + MaterialTheme.padding.medium,
                    ),
                style = RichTextStyle(
                    stringStyle = RichTextStringStyle(
                        linkStyle = SpanStyle(color = MaterialTheme.colorScheme.primary),
                    ),
                ),
            ) {
                Markdown(content = state.notes.orEmpty())
            }
        }
    }
}
