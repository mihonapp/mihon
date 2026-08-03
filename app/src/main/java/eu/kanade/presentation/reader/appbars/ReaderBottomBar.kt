package eu.kanade.presentation.reader.appbars

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.reader.setting.ReaderOrientation
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun ReaderBottomBar(
    readingMode: ReadingMode,
    onClickReadingMode: () -> Unit,
    orientation: ReaderOrientation,
    onClickOrientation: () -> Unit,
    cropEnabled: Boolean,
    onClickCropBorder: () -> Unit,
    showShiftPairing: Boolean,
    spreadShifted: Boolean,
    isRightToLeft: Boolean,
    onClickShiftPairing: () -> Unit,
    onClickSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .pointerInput(Unit) {},
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClickReadingMode) {
            Icon(
                painter = painterResource(readingMode.iconRes),
                contentDescription = stringResource(MR.strings.viewer),
            )
        }

        IconButton(onClick = onClickOrientation) {
            Icon(
                imageVector = orientation.icon,
                contentDescription = stringResource(MR.strings.rotation_type),
            )
        }

        IconButton(onClick = onClickCropBorder) {
            Icon(
                painter = painterResource(if (cropEnabled) R.drawable.ic_crop_24dp else R.drawable.ic_crop_off_24dp),
                contentDescription = stringResource(MR.strings.pref_crop_borders),
            )
        }

        if (showShiftPairing) {
            // The icon's solid block is the page standing alone (the framed pair is the spread). That
            // lone page sits at the end of reading order by default and at the start once shifted,
            // and reading order's start/end is a different physical side per direction (start is the
            // left in L2R, the right in R2L). The two drawables are fixed left/right shapes, so which
            // one means "shifted" swaps when reading direction flips.
            val showShiftedIcon = spreadShifted != isRightToLeft
            IconButton(onClick = onClickShiftPairing) {
                Icon(
                    painter = painterResource(
                        if (showShiftedIcon) {
                            R.drawable.ic_reader_shift_pairing_shifted_24dp
                        } else {
                            R.drawable.ic_reader_shift_pairing_24dp
                        },
                    ),
                    contentDescription = stringResource(MR.strings.action_shift_page_pairing),
                )
            }
        }

        IconButton(onClick = onClickSettings) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = stringResource(MR.strings.action_settings),
            )
        }
    }
}
