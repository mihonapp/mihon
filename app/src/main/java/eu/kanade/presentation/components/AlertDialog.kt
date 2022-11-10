package eu.kanade.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AlertDialogContent(
    buttons: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .sizeIn(minWidth = MinWidth, maxWidth = MaxWidth)
            .padding(DialogPadding),
    ) {
        icon?.let {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.secondary) {
                Box(
                    Modifier
                        .padding(IconPadding)
                        .align(Alignment.CenterHorizontally),
                ) {
                    icon()
                }
            }
        }
        title?.let {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                val textStyle = MaterialTheme.typography.headlineSmall
                ProvideTextStyle(textStyle) {
                    Box(
                        // Align the title to the center when an icon is present.
                        Modifier
                            .padding(TitlePadding)
                            .align(
                                if (icon == null) {
                                    Alignment.Start
                                } else {
                                    Alignment.CenterHorizontally
                                },
                            ),
                    ) {
                        title()
                    }
                }
            }
        }
        text?.let {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                val textStyle = MaterialTheme.typography.bodyMedium
                ProvideTextStyle(textStyle) {
                    Box(
                        Modifier
                            .weight(weight = 1f, fill = false)
                            .padding(TextPadding)
                            .align(Alignment.Start),
                    ) {
                        text()
                    }
                }
            }
        }
        Box(modifier = Modifier.align(Alignment.End)) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) {
                val textStyle = MaterialTheme.typography.labelLarge
                ProvideTextStyle(value = textStyle, content = buttons)
            }
        }
    }
}

// Paddings for each of the dialog's parts.
private val DialogPadding = PaddingValues(all = 24.dp)
private val IconPadding = PaddingValues(bottom = 16.dp)
private val TitlePadding = PaddingValues(bottom = 16.dp)
private val TextPadding = PaddingValues(bottom = 24.dp)

private val MinWidth = 280.dp
private val MaxWidth = 560.dp
