package mihon.desktop.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import mihon.desktop.i18n.LocalStrings

@Composable
internal fun ReaderChapterNavigator(
    currentPage: Int,
    totalPages: Int,
    isRtl: Boolean,
    onPageSelected: (Int) -> Unit,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
    hasPreviousChapter: Boolean,
    hasNextChapter: Boolean,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val background = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp).copy(alpha = 0.94f)
    val buttonColors = IconButtonDefaults.filledIconButtonColors(
        containerColor = background,
        disabledContainerColor = background.copy(alpha = 0.6f),
    )
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .testTag("reader-chapter-navigator"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledIconButton(
                onClick = if (isRtl) onNextChapter else onPreviousChapter,
                enabled = if (isRtl) hasNextChapter else hasPreviousChapter,
                colors = buttonColors,
                modifier = Modifier.testTag(
                    if (isRtl) "reader-scrubber-next-chapter" else "reader-scrubber-prev-chapter",
                ),
            ) {
                Icon(
                    Icons.Rounded.SkipPrevious,
                    contentDescription = if (isRtl) strings.readerNextChapter else strings.readerPreviousChapter,
                )
            }

            if (totalPages > 1) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(background)
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StablePageLabel(
                        currentPage,
                        totalPages,
                        Modifier.testTag("reader-current-page"),
                    )
                    CompositionLocalProvider(
                        LocalLayoutDirection provides if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                    ) {
                        Box(
                            Modifier
                                .weight(1f)
                                .testTag(if (isRtl) "reader-scrubber-slider-rtl" else "reader-scrubber-slider-ltr"),
                        ) {
                            Slider(
                                value = currentPage.toFloat(),
                                onValueChange = {
                                    onPageSelected((it.toInt() - 1).coerceIn(0, totalPages - 1))
                                },
                                valueRange = 1f..totalPages.toFloat(),
                                steps = (totalPages - 2).coerceAtLeast(0),
                                modifier = Modifier.fillMaxWidth().testTag("reader-scrubber-slider"),
                            )
                        }
                    }
                    StablePageLabel(
                        totalPages,
                        totalPages,
                        Modifier.testTag("reader-total-pages"),
                    )
                }
            } else {
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (totalPages == 0) "0 / 0" else "1 / 1",
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
            }

            FilledIconButton(
                onClick = if (isRtl) onPreviousChapter else onNextChapter,
                enabled = if (isRtl) hasPreviousChapter else hasNextChapter,
                colors = buttonColors,
                modifier = Modifier.testTag(
                    if (isRtl) "reader-scrubber-prev-chapter" else "reader-scrubber-next-chapter",
                ),
            ) {
                Icon(
                    Icons.Rounded.SkipNext,
                    contentDescription = if (isRtl) strings.readerPreviousChapter else strings.readerNextChapter,
                )
            }
        }
    }
}

@Composable
private fun StablePageLabel(value: Int, totalPages: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = Modifier.width(((totalPages.toString().length * 9) + 8).dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Text(text = value.toString(), modifier = modifier, style = MaterialTheme.typography.labelLarge)
    }
}
