package eu.kanade.presentation.reader.appbars

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Bookmark
import mihon.icons.materialsymbols.rounded.Cast
import mihon.icons.materialsymbols.rounded.CastConnected
import mihon.icons.materialsymbols.roundedfilled.Bookmark
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun ReaderTopBar(
    mangaTitle: String?,
    chapterTitle: String?,
    navigateUp: () -> Unit,
    bookmarked: Boolean,
    onToggleBookmarked: () -> Unit,
    onOpenInWebView: (() -> Unit)?,
    onOpenInBrowser: (() -> Unit)?,
    onShare: (() -> Unit)?,
    castActive: Boolean,
    onClickCast: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppBar(
        modifier = modifier,
        backgroundColor = Color.Transparent,
        title = mangaTitle,
        subtitle = chapterTitle,
        navigateUp = navigateUp,
        actions = {
            AppBarActions(
                actions = buildList {
                    add(
                        AppBar.Action(
                            title = stringResource(
                                if (bookmarked) {
                                    MR.strings.action_remove_bookmark
                                } else {
                                    MR.strings.action_bookmark
                                },
                            ),
                            icon = if (bookmarked) {
                                MaterialSymbols.RoundedFilled.Bookmark
                            } else {
                                MaterialSymbols.Rounded.Bookmark
                            },
                            onClick = onToggleBookmarked,
                        ),
                    )
                    add(
                        AppBar.Action(
                            title = stringResource(MR.strings.cast_action_cast),
                            icon = if (castActive) {
                                MaterialSymbols.Rounded.CastConnected
                            } else {
                                MaterialSymbols.Rounded.Cast
                            },
                            iconTint = if (castActive) MaterialTheme.colorScheme.primary else null,
                            onClick = onClickCast,
                        ),
                    )
                    onOpenInWebView?.let {
                        add(
                            AppBar.OverflowAction(
                                title = stringResource(MR.strings.action_open_in_web_view),
                                onClick = it,
                            ),
                        )
                    }
                    onOpenInBrowser?.let {
                        add(
                            AppBar.OverflowAction(
                                title = stringResource(MR.strings.action_open_in_browser),
                                onClick = it,
                            ),
                        )
                    }
                    onShare?.let {
                        add(
                            AppBar.OverflowAction(
                                title = stringResource(MR.strings.action_share),
                                onClick = it,
                            ),
                        )
                    }
                },
            )
        },
    )
}
