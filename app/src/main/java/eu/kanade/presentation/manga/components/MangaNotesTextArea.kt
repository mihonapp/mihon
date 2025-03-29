package eu.kanade.presentation.manga.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.FormatUnderlined
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichTextEditor
import com.mohamedrejeb.richeditor.ui.material3.RichTextEditorDefaults.richTextEditorColors
import eu.kanade.tachiyomi.ui.manga.notes.MangaNotesScreen
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.time.Duration.Companion.seconds

private const val MAX_LENGTH = 250
private const val MAX_LENGTH_WARN = MAX_LENGTH * 0.9

@Composable
fun MangaNotesTextArea(
    state: MangaNotesScreen.State,
    onUpdate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val richTextState = rememberRichTextState()
    val primaryColor = MaterialTheme.colorScheme.primary

    DisposableEffect(scope, richTextState) {
        snapshotFlow { richTextState.annotatedString }
            .debounce(0.25.seconds)
            .distinctUntilChanged()
            .map { richTextState.toMarkdown() }
            .onEach { onUpdate(it) }
            .launchIn(scope)

        onDispose {
            onUpdate(richTextState.toMarkdown())
        }
    }
    LaunchedEffect(Unit) {
        richTextState.setMarkdown(state.notes)
        richTextState.config.unorderedListIndent = 4
        richTextState.config.orderedListIndent = 20
    }
    LaunchedEffect(primaryColor) {
        richTextState.config.linkColor = primaryColor
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(focusRequester) {
        focusRequester.requestFocus()
    }
    val textLength = remember(richTextState.annotatedString) { richTextState.toText().length }

    Column(
        modifier = modifier
            .padding(horizontal = MaterialTheme.padding.small)
            .fillMaxSize(),
    ) {
        RichTextEditor(
            state = richTextState,
            textStyle = MaterialTheme.typography.bodyLarge,
            maxLength = MAX_LENGTH,
            placeholder = {
                Text(text = stringResource(MR.strings.notes_placeholder))
            },
            colors = richTextEditorColors(
                containerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            contentPadding = PaddingValues(
                horizontal = MaterialTheme.padding.medium,
            ),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .focusRequester(focusRequester),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .padding(vertical = MaterialTheme.padding.small)
                .fillMaxWidth(),
        ) {
            LazyRow(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                item {
                    MangaNotesTextAreaButton(
                        onClick = { richTextState.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold)) },
                        isSelected = richTextState.currentSpanStyle.fontWeight == FontWeight.Bold,
                        icon = Icons.Outlined.FormatBold,
                    )
                }
                item {
                    MangaNotesTextAreaButton(
                        onClick = { richTextState.toggleSpanStyle(SpanStyle(fontStyle = FontStyle.Italic)) },
                        isSelected = richTextState.currentSpanStyle.fontStyle == FontStyle.Italic,
                        icon = Icons.Outlined.FormatItalic,
                    )
                }
                item {
                    MangaNotesTextAreaButton(
                        onClick = {
                            richTextState.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.Underline))
                        },
                        isSelected = richTextState.currentSpanStyle.textDecoration
                            ?.contains(TextDecoration.Underline)
                            ?: false,
                        icon = Icons.Outlined.FormatUnderlined,
                    )
                }
                item {
                    VerticalDivider(
                        modifier = Modifier
                            .padding(horizontal = MaterialTheme.padding.extraSmall)
                            .height(MaterialTheme.padding.large),
                    )
                }
                item {
                    MangaNotesTextAreaButton(
                        onClick = { richTextState.toggleUnorderedList() },
                        isSelected = richTextState.isUnorderedList,
                        icon = Icons.AutoMirrored.Outlined.FormatListBulleted,
                    )
                }
                item {
                    MangaNotesTextAreaButton(
                        onClick = { richTextState.toggleOrderedList() },
                        isSelected = richTextState.isOrderedList,
                        icon = Icons.Outlined.FormatListNumbered,
                    )
                }
            }

            Box(
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = (MAX_LENGTH - textLength).toString(),
                    color = if (textLength > MAX_LENGTH_WARN) {
                        MaterialTheme.colorScheme.error
                    } else {
                        Color.Unspecified
                    },
                    modifier = Modifier.padding(MaterialTheme.padding.extraSmall),
                )
            }
        }
    }
}

@Composable
fun MangaNotesTextAreaButton(
    onClick: () -> Unit,
    icon: ImageVector,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(
                onClick = onClick,
                enabled = true,
                role = Role.Button,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = icon.name,
            tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .background(color = if (isSelected) MaterialTheme.colorScheme.onBackground else Color.Transparent)
                .padding(MaterialTheme.padding.extraSmall),
        )
    }
}
