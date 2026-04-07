package eu.kanade.presentation.reader.appbars

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.reader.setting.ReaderOrientation
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReaderBottomBar(
    readingMode: ReadingMode,
    onClickReadingMode: () -> Unit,
    orientation: ReaderOrientation,
    onClickOrientation: () -> Unit,
    cropEnabled: Boolean,
    onClickCropBorder: () -> Unit,
    onClickOcr: () -> Unit,
    onLongClickOcr: () -> Unit,
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

        Box(
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .clip(CircleShape)
                .combinedClickable(
                    onClick = onClickOcr,
                    onLongClick = onLongClickOcr,
                    role = Role.Button,
                )
                .padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Translate,
                contentDescription = stringResource(MR.strings.action_translate),
            )
        }

        IconButton(onClick = onClickSettings) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = stringResource(MR.strings.action_settings),
            )
        }
    }
}
