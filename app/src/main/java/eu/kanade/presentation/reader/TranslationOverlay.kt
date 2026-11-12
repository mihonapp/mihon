package eu.kanade.presentation.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.components.displayName
import eu.kanade.presentation.components.message
import eu.kanade.tachiyomi.data.translation.TranslatedPage
import eu.kanade.tachiyomi.data.translation.TranslatedTextBlock
import eu.kanade.tachiyomi.data.translation.TranslationError
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Close
import mihon.icons.materialsymbols.rounded.Public
import tachiyomi.domain.translation.model.TranslationLanguage
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * What [TranslationOverlay] is currently showing.
 */
data class TranslationOverlayState(
    val loading: Boolean = false,
    val page: TranslatedPage? = null,
    val error: TranslationError? = null,
    val errorDetail: String? = null,
)

/**
 * Draws the translated text over the page it came from, at the position the original text was read from.
 *
 * The page is shown on its own instead of over the reader's view so the same overlay works in every reading mode,
 * including the long strip where pages are cropped and joined into strips.
 */
@Composable
fun TranslationOverlay(
    state: TranslationOverlayState,
    sourceLanguage: TranslationLanguage,
    targetLanguage: TranslationLanguage,
    onSourceLanguageChange: (TranslationLanguage) -> Unit,
    onTargetLanguageChange: (TranslationLanguage) -> Unit,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
) {
    val page = state.page
    val error = state.error

    BackHandler(onBack = onDismiss)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
            // Keeps taps and swipes on the page underneath from turning pages while it's covered.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        when {
            page != null -> {
                TranslatedPageContent(page = page)
            }
            error != null -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = error.message(state.errorDetail),
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    TextButton(onClick = onRetry) {
                        Text(stringResource(MR.strings.action_retry))
                    }
                }
            }
            state.loading -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CircularProgressIndicator(color = Color.White)
                    Text(
                        text = stringResource(MR.strings.translation_loading),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = MaterialSymbols.Rounded.Close,
                    contentDescription = stringResource(MR.strings.action_close),
                    tint = Color.White,
                )
            }
            LanguageSelector(
                modifier = Modifier.weight(1f),
                language = sourceLanguage,
                label = stringResource(MR.strings.translation_from),
                allowAuto = true,
                onSelect = onSourceLanguageChange,
            )
            Icon(
                imageVector = MaterialSymbols.Rounded.Public,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
            )
            LanguageSelector(
                modifier = Modifier.weight(1f),
                language = targetLanguage,
                label = stringResource(MR.strings.translation_to),
                allowAuto = false,
                onSelect = onTargetLanguageChange,
            )
        }
    }
}

@Composable
private fun TranslatedPageContent(page: TranslatedPage) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val image = remember(page.bitmap) { page.bitmap.asImageBitmap() }
        val imageWidth = with(density) { page.bitmap.width.toDp() }
        val imageHeight = with(density) { page.bitmap.height.toDp() }
        val scale = minOf(maxWidth / imageWidth, maxHeight / imageHeight)
        val displayedWidth = imageWidth * scale
        val displayedHeight = imageHeight * scale
        val offsetX = (maxWidth - displayedWidth) / 2f
        val offsetY = (maxHeight - displayedHeight) / 2f

        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .offset(x = offsetX, y = offsetY)
                .size(width = displayedWidth, height = displayedHeight),
        )

        page.blocks.forEach { block ->
            TranslatedBlock(
                block = block,
                scale = scale,
                offsetX = offsetX,
                offsetY = offsetY,
            )
        }
    }
}

@Composable
private fun TranslatedBlock(
    block: TranslatedTextBlock,
    scale: Float,
    offsetX: Dp,
    offsetY: Dp,
) {
    val density = LocalDensity.current
    val left = with(density) { block.box.left.toDp() } * scale
    val top = with(density) { block.box.top.toDp() } * scale
    val width = with(density) { block.box.width().toDp() } * scale
    val height = with(density) { block.box.height().toDp() } * scale
    if (width.value <= 0f || height.value <= 0f) return

    val fontSize = (height.value / 2.4f).coerceIn(7f, 22f)
    val maxLines = (height.value / (fontSize * 1.25f)).toInt().coerceAtLeast(1)

    Box(
        modifier = Modifier
            .offset(x = offsetX + left, y = offsetY + top)
            .size(width = width, height = height)
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                shape = RoundedCornerShape(3.dp),
            )
            .padding(2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = block.translated,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = fontSize.sp,
            lineHeight = (fontSize * 1.2f).sp,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LanguageSelector(
    language: TranslationLanguage,
    label: String,
    allowAuto: Boolean,
    onSelect: (TranslationLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val languages = remember(allowAuto) {
        TranslationLanguage.entries.filter { allowAuto || it != TranslationLanguage.AUTO }
    }

    Box(modifier = modifier) {
        Text(
            text = "$label: ${language.displayName()}",
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .clickable { expanded = true }
                .padding(vertical = 8.dp, horizontal = 4.dp),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            languages.forEach { entry ->
                DropdownMenuItem(
                    text = { Text(entry.displayName()) },
                    onClick = {
                        expanded = false
                        onSelect(entry)
                    },
                )
            }
        }
    }
}
