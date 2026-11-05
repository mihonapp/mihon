package eu.kanade.presentation.reader

import android.graphics.BitmapFactory
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Check
import mihon.icons.materialsymbols.rounded.Close
import mihon.icons.materialsymbols.rounded.Preview
import mihon.icons.materialsymbols.rounded.Save
import mihon.icons.materialsymbols.rounded.Share
import mihon.icons.materialsymbols.roundedfilled.Visibility
import mihon.icons.materialsymbols.roundedfilled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import eu.kanade.tachiyomi.R
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun LiveTranslationDialog(
    originalBytes: ByteArray,
    translatedBytes: ByteArray,
    onDismissRequest: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onApplyToReader: (() -> Unit)? = null,
    onTranslateAllPages: (() -> Unit)? = null,
) {
    var peekingOriginal by remember { mutableStateOf(false) }
    var toggledOriginal by remember { mutableStateOf(false) }

    val originalBitmap = remember(originalBytes) {
        BitmapFactory.decodeByteArray(originalBytes, 0, originalBytes.size)?.asImageBitmap()
    }
    val translatedBitmap = remember(translatedBytes) {
        BitmapFactory.decodeByteArray(translatedBytes, 0, translatedBytes.size)?.asImageBitmap()
    }

    val showOriginal = peekingOriginal || toggledOriginal

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
            ) {
                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(MR.strings.translation_live_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (showOriginal) {
                                    MaterialTheme.colorScheme.tertiaryContainer
                                } else {
                                    MaterialTheme.colorScheme.primaryContainer
                                },
                            ),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(
                                text = if (showOriginal) {
                                    stringResource(MR.strings.translation_view_original)
                                } else {
                                    stringResource(MR.strings.translation_view_translated)
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }

                    IconButton(onClick = onDismissRequest) {
                        Icon(
                            imageVector = MaterialSymbols.Rounded.Close,
                            contentDescription = stringResource(MR.strings.action_close),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Image viewer with hold-to-peek original gesture
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color.Black, RoundedCornerShape(12.dp))
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    peekingOriginal = true
                                    tryAwaitRelease()
                                    peekingOriginal = false
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Crossfade(
                        targetState = showOriginal,
                        label = "TranslationCrossfade",
                    ) { isOriginal ->
                        val bitmapToDisplay = if (isOriginal) originalBitmap else translatedBitmap
                        if (bitmapToDisplay != null) {
                            Image(
                                bitmap = bitmapToDisplay,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                            )
                        }
                    }

                    // Bottom pill showing "Hold to peek original"
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp),
                        color = Color.Black.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = if (showOriginal) MaterialSymbols.RoundedFilled.VisibilityOff else MaterialSymbols.RoundedFilled.Visibility,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = stringResource(MR.strings.translation_hold_to_peek),
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Controls and Action Buttons - Row 1 (Secondary actions)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Toggle compare button
                    FilledTonalButton(
                        onClick = { toggledOriginal = !toggledOriginal },
                        modifier = Modifier.weight(1.2f),
                    ) {
                        Icon(
                            imageVector = MaterialSymbols.Rounded.Preview,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (toggledOriginal) {
                                stringResource(MR.strings.translation_view_translated)
                            } else {
                                stringResource(MR.strings.translation_view_original)
                            },
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }

                    // Save button
                    OutlinedButton(
                        onClick = onSave,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = MaterialSymbols.Rounded.Save,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(MR.strings.action_save),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }

                    // Share button
                    OutlinedButton(
                        onClick = onShare,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = MaterialSymbols.Rounded.Share,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(MR.strings.action_share),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Action Buttons - Row 2 (Primary Reader actions)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Apply to Reader button
                    if (onApplyToReader != null) {
                        Button(
                            onClick = {
                                onApplyToReader()
                                onDismissRequest()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                            ),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = MaterialSymbols.Rounded.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(MR.strings.translation_live_apply),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }

                    // Translate Entire Chapter button
                    if (onTranslateAllPages != null) {
                        Button(
                            onClick = {
                                onApplyToReader?.invoke()
                                onTranslateAllPages()
                                onDismissRequest()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary,
                            ),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = ImageVector.vectorResource(R.drawable.ic_translate_24dp),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(MR.strings.translation_live_translate_all),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}
