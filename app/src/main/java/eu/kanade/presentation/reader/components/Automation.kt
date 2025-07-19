package eu.kanade.presentation.reader.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.icerock.moko.resources.format
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import eu.kanade.presentation.util.isTabletUi
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.viewer.Viewer
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerViewer
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonViewer
import eu.kanade.tachiyomi.util.system.toast
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun Automation(
    readerPreferences: ReaderPreferences,
    viewer: Viewer?
) {
    val isPagerViewer = viewer is PagerViewer
    val isWebtoonViewer = viewer is WebtoonViewer

    if (!((isPagerViewer && readerPreferences.autoFlip().get()) ||
          (isWebtoonViewer && readerPreferences.autoScroll().get()))) return

    android.util.Log.d("Automation","Init automation $isPagerViewer $isWebtoonViewer")
    val isTabletUi = isTabletUi()
    val horizontalPadding = if (isTabletUi) 24.dp else 8.dp

    val context = LocalContext.current

    // Match with toolbar background color set in ReaderActivity
    val backgroundColor =
        MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp).copy(alpha = if (isSystemInDarkTheme()) 0.9f else 0.95f)
    val buttonColor = IconButtonDefaults.filledIconButtonColors(
        containerColor = backgroundColor,
        disabledContainerColor = backgroundColor,
    )



    // We explicitly handle direction based on the reader viewer rather than the system direction
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding),
            horizontalAlignment = Alignment.End,
        ) {
            FilledIconButton(
                onClick = {
                    if (isPagerViewer) {
                        val newAutoFlipInterval = (readerPreferences.autoFlipInterval().get() + 1).coerceIn(1, 60)
                        readerPreferences.autoFlipInterval().set(newAutoFlipInterval)
                        context.toast(MR.strings.pref_auto_flip_interval_summary
                            .format(newAutoFlipInterval).toString(context))
                    }
                    if (isWebtoonViewer) {
                        val newAutoScrollSpeed = (readerPreferences.autoScrollSpeed().get() + 5).coerceIn(5, 30)
                        readerPreferences.autoScrollSpeed().set(newAutoScrollSpeed)
                        context.toast(MR.strings.pref_auto_scroll_speed_summary
                            .format(newAutoScrollSpeed).toString(context))
                    }
                },
                colors = buttonColor,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = stringResource(
                        MR.strings.action_automation_increase_speed,
                    ),
                )
            }
            FilledIconButton(
                onClick = {
                    android.util.Log.d("Automation","Clicked start automation")
                    viewer?.automationInProgress?.value = true
                },
                colors = buttonColor,
            ) {
                Icon(
                    imageVector = Icons.Outlined.PlayArrow,
                    contentDescription = stringResource(
                        MR.strings.action_automation_start,
                    ),
                )
            }

            FilledIconButton(
                onClick = {
                    android.util.Log.d("Automation","Decrement automation")
                    if (isPagerViewer) {
                        val newAutoFlipInterval = (readerPreferences.autoFlipInterval().get() - 1).coerceIn(1, 60)
                        readerPreferences.autoFlipInterval().set(newAutoFlipInterval)
                        context.toast(MR.strings.pref_auto_flip_interval_summary
                            .format(newAutoFlipInterval).toString(context))
                    }
                    if (isWebtoonViewer) {
                        val newAutoScrollSpeed = (readerPreferences.autoScrollSpeed().get() - 5).coerceIn(5, 30)
                        readerPreferences.autoScrollSpeed().set(newAutoScrollSpeed)
                        context.toast(MR.strings.pref_auto_scroll_speed_summary
                            .format(newAutoScrollSpeed).toString(context))
                    }
                },
                colors = buttonColor,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Remove,
                    contentDescription = stringResource(
                        MR.strings.action_automation_decrease_speed,
                    ),
                )
            }
        }
    }
}

@Preview
@Composable
private fun AutomationPreview() {
    val fakePreferenceStore = InMemoryPreferenceStore()
    val readerPreferences = ReaderPreferences(fakePreferenceStore)
    TachiyomiPreviewTheme {
        Automation(
            readerPreferences = readerPreferences,
            viewer = null,
        )
    }
}
